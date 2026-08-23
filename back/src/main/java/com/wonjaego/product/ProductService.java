package com.wonjaego.product;

import com.wonjaego.member.Member;
import com.wonjaego.member.MemberRepository;
import com.wonjaego.movement.Movement;
import com.wonjaego.movement.MovementRepository;
import com.wonjaego.movement.MovementType;
import com.wonjaego.storage.FileStorage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final Set<String> ALLOWED_PHOTO_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_PHOTO_SIZE_BYTES = 5L * 1024 * 1024;
    private static final String INITIAL_STOCK_MEMO = "상품 등록 시 초기 재고";
    private static final int MAX_COMBINATION_COUNT = 500;

    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;
    private final MovementRepository movementRepository;
    private final OptionGroupRepository optionGroupRepository;
    private final OptionValueRepository optionValueRepository;
    private final ProductVariantRepository productVariantRepository;
    private final FileStorage fileStorage;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<Product> listOwned(Long memberId) {
        return productRepository.findAllByMemberId(memberId);
    }

    @Transactional(readOnly = true)
    public Product getOwned(Long memberId, Long productId) {
        return productRepository.findByIdAndMemberId(productId, memberId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    @Transactional
    public Product create(Long memberId, ProductCreateForm form) {
        boolean hasPhoto = hasPhoto(form.getPhoto());
        // Validate everything before writing anything — an invalid photo or a mismatched
        // stock payload must not leave a Product/OptionGroup/OptionValue row behind. (A
        // caller sharing this transaction, e.g. an integration test asserting no row exists
        // after rejection, would otherwise see the not-yet-rolled-back insert.)
        if (hasPhoto) {
            validatePhoto(form.getPhoto());
        }

        List<ParsedOptionGroup> optionGroups = parseOptionGroups(form.getOptionGroups());
        int expectedCombinationCount = combinationCount(optionGroups);
        List<Integer> initialStocks = parseStocks(form.getStocksJson(), expectedCombinationCount);

        Member member = memberRepository.getReferenceById(memberId);
        Product product = productRepository.save(new Product(member, form.getName(), form.getPrice()));

        List<List<OptionValue>> groupsOfValues = new ArrayList<>();
        for (ParsedOptionGroup group : optionGroups) {
            groupsOfValues.add(saveOptionGroup(product, group.name(), group.values()));
        }

        List<Set<OptionValue>> combinations = cartesianProduct(groupsOfValues);
        // Inlined rather than delegated to MovementService.record() — MovementService already
        // depends on ProductService (for ownership checks), so calling back here would be
        // circular. Kept in sync by hand: adjustStock() + save(Movement) here must mirror
        // record()'s INBOUND branch exactly.
        for (int i = 0; i < combinations.size(); i++) {
            ProductVariant variant = productVariantRepository.save(new ProductVariant(product, combinations.get(i)));
            int initialStock = initialStocks.get(i);
            if (initialStock > 0) {
                variant.adjustStock(initialStock);
                movementRepository.save(new Movement(variant, null, MovementType.INBOUND, initialStock, INITIAL_STOCK_MEMO));
            }
        }

        // Store the photo last, after every other write in this transaction — file writes
        // aren't transactional, so if anything above this line were to throw and roll back
        // the DB, a photo stored earlier would be orphaned (written but never referenced).
        if (hasPhoto) {
            product.updatePhotoKey(storePhoto(form.getPhoto()));
        }

        return product;
    }

    @Transactional
    public Product update(Long memberId, Long productId, ProductEditForm form) {
        Product product = getOwned(memberId, productId);
        boolean hasPhoto = hasPhoto(form.getPhoto());
        // Validate before mutating the managed entity — an invalid photo must not leave
        // updateInfo()'s change visible even in-memory (same reasoning as create()).
        if (hasPhoto) {
            validatePhoto(form.getPhoto());
        }

        product.updateInfo(form.getName(), form.getPrice());

        if (hasPhoto) {
            String oldPhotoKey = product.getPhotoKey();
            // Store the new photo before touching the old one — if storing fails, the
            // product keeps its original (still valid) photo rather than losing it.
            product.updatePhotoKey(storePhoto(form.getPhoto()));
            if (oldPhotoKey != null) {
                fileStorage.delete(oldPhotoKey);
            }
        }

        return product;
    }

    @Transactional
    public void delete(Long memberId, Long productId) {
        Product product = getOwned(memberId, productId);
        if (movementRepository.existsByVariant_ProductId(productId)) {
            throw new ProductHasMovementsException(product.getName());
        }
        if (product.getPhotoKey() != null) {
            fileStorage.delete(product.getPhotoKey());
        }
        productVariantRepository.deleteAllByProductId(productId);
        optionValueRepository.deleteAllByOptionGroup_ProductId(productId);
        optionGroupRepository.deleteAllByProductId(productId);
        productRepository.delete(product);
    }

    private boolean hasPhoto(MultipartFile photo) {
        return photo != null && !photo.isEmpty();
    }

    private void validatePhoto(MultipartFile photo) {
        // Set.of(...) throws NullPointerException on contains(null) — a client that omits
        // the Content-Type header entirely must still be rejected gracefully, not with a 500.
        String contentType = photo.getContentType();
        if (contentType == null || !ALLOWED_PHOTO_CONTENT_TYPES.contains(contentType)) {
            throw new InvalidPhotoException("JPEG, PNG, WebP 형식만 업로드할 수 있습니다.");
        }
        if (photo.getSize() > MAX_PHOTO_SIZE_BYTES) {
            throw new InvalidPhotoException("사진 파일은 5MB 이하만 업로드할 수 있습니다.");
        }
    }

    private String storePhoto(MultipartFile photo) {
        try {
            return fileStorage.store(photo);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record ParsedOptionGroup(String name, List<String> values) {
    }

    // A row with a blank name or a values text that parses to zero values (including a
    // comma-only input like ",,,") is silently dropped — it's treated the same as a row the
    // user added and never filled in, not rejected.
    private List<ParsedOptionGroup> parseOptionGroups(List<ProductCreateForm.OptionGroupInput> inputs) {
        List<ParsedOptionGroup> groups = new ArrayList<>();
        for (ProductCreateForm.OptionGroupInput input : inputs) {
            String name = input.getName() == null ? "" : input.getName().trim();
            List<String> values = parseDistinctValues(input.getValuesText());
            if (name.isEmpty() || values.isEmpty()) {
                continue;
            }
            groups.add(new ParsedOptionGroup(name, values));
        }
        return groups;
    }

    // Trim/split/dedupe a comma-separated option-values string.
    private List<String> parseDistinctValues(String valuesText) {
        if (valuesText == null || valuesText.isBlank()) {
            return List.of();
        }
        return Arrays.stream(valuesText.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }

    // Accumulates with an early exit past the cap rather than computing the raw product
    // first — a few option groups with many values each can overflow int before a plain
    // "compute then compare" check would ever see it.
    private int combinationCount(List<ParsedOptionGroup> groups) {
        int count = 1;
        for (ParsedOptionGroup group : groups) {
            count *= group.values().size();
            if (count > MAX_COMBINATION_COUNT) {
                throw new InvalidStockDataException("옵션 조합이 너무 많습니다. 조합 수를 " + MAX_COMBINATION_COUNT + "개 이하로 줄여주세요.");
            }
        }
        return count;
    }

    // Blank/missing stocksJson defaults to "no initial stock for any combination" — a
    // caller that doesn't care about initial stock (e.g. seed data) shouldn't have to
    // spell out an all-zero array of the right length.
    private List<Integer> parseStocks(String stocksJson, int expectedCount) {
        if (stocksJson == null || stocksJson.isBlank()) {
            return new ArrayList<>(Collections.nCopies(expectedCount, 0));
        }
        List<Integer> stocks;
        try {
            stocks = objectMapper.readValue(stocksJson, new TypeReference<List<Integer>>() { });
        } catch (JacksonException e) {
            throw new InvalidStockDataException("재고 데이터 형식이 올바르지 않습니다.");
        }
        if (stocks.size() != expectedCount) {
            throw new InvalidStockDataException("재고 입력 개수가 옵션 조합 개수와 일치하지 않습니다.");
        }
        if (stocks.stream().anyMatch(stock -> stock == null || stock < 0)) {
            throw new InvalidStockDataException("재고는 0 이상의 숫자여야 합니다.");
        }
        return stocks;
    }

    private List<OptionValue> saveOptionGroup(Product product, String name, List<String> values) {
        OptionGroup group = optionGroupRepository.save(new OptionGroup(product, name));
        return values.stream()
                .map(value -> optionValueRepository.save(new OptionValue(group, value)))
                .toList();
    }

    // Cartesian product of each option group's values. No groups -> one empty combination
    // (the product itself is the only variant), matching the "0 options = single unit" rule.
    private List<Set<OptionValue>> cartesianProduct(List<List<OptionValue>> groupsOfValues) {
        List<Set<OptionValue>> combinations = new ArrayList<>();
        combinations.add(new LinkedHashSet<>());
        for (List<OptionValue> values : groupsOfValues) {
            List<Set<OptionValue>> next = new ArrayList<>();
            for (Set<OptionValue> partial : combinations) {
                for (OptionValue value : values) {
                    Set<OptionValue> combination = new LinkedHashSet<>(partial);
                    combination.add(value);
                    next.add(combination);
                }
            }
            combinations = next;
        }
        return combinations;
    }
}
