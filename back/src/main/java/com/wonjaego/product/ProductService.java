package com.wonjaego.product;

import com.wonjaego.member.Member;
import com.wonjaego.member.MemberRepository;
import com.wonjaego.movement.MovementRepository;
import com.wonjaego.storage.FileStorage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final Set<String> ALLOWED_PHOTO_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_PHOTO_SIZE_BYTES = 5L * 1024 * 1024;

    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;
    private final MovementRepository movementRepository;
    private final OptionGroupRepository optionGroupRepository;
    private final OptionValueRepository optionValueRepository;
    private final ProductVariantRepository productVariantRepository;
    private final FileStorage fileStorage;

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
        // Validate before writing anything — an invalid photo must not leave a Product row
        // behind. (A caller sharing this transaction, e.g. an integration test asserting no
        // row exists after rejection, would otherwise see the not-yet-rolled-back insert.)
        if (hasPhoto) {
            validatePhoto(form.getPhoto());
        }

        Member member = memberRepository.getReferenceById(memberId);
        Product product = productRepository.save(new Product(member, form.getName(), form.getPrice()));

        List<List<OptionValue>> groupsOfValues = new ArrayList<>();
        for (ProductCreateForm.OptionGroupInput input : form.getOptionGroups()) {
            String groupName = input.getName() == null ? "" : input.getName().trim();
            String valuesText = input.getValuesText() == null ? "" : input.getValuesText().trim();
            if (groupName.isEmpty() || valuesText.isEmpty()) {
                continue;
            }
            List<String> distinctValues = Arrays.stream(valuesText.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .distinct()
                    .toList();
            // A comma-only input (e.g. ",,,") is non-blank but parses to zero values — skip
            // it like an unused slot, since an OptionGroup with no values would collapse the
            // whole cartesian product to nothing, leaving the product with no variants at all.
            if (distinctValues.isEmpty()) {
                continue;
            }
            OptionGroup group = optionGroupRepository.save(new OptionGroup(product, groupName));
            List<OptionValue> values = distinctValues.stream()
                    .map(value -> optionValueRepository.save(new OptionValue(group, value)))
                    .toList();
            groupsOfValues.add(values);
        }

        for (Set<OptionValue> combination : cartesianProduct(groupsOfValues)) {
            productVariantRepository.save(new ProductVariant(product, combination));
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
