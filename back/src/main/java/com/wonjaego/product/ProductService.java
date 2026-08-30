package com.wonjaego.product;

import com.wonjaego.member.Member;
import com.wonjaego.member.MemberRepository;
import com.wonjaego.member.MemberService;
import com.wonjaego.movement.Movement;
import com.wonjaego.movement.MovementRepository;
import com.wonjaego.movement.MovementType;
import com.wonjaego.storage.FileStorage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
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
    private final MemberService memberService;
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

    // Backs the 입고하기/출고하기 screens' product-name search. Capped in the query itself
    // so a broad query (e.g. a single common character) can't return an unbounded result set.
    @Transactional(readOnly = true)
    public List<Product> search(Long memberId, String query) {
        return productRepository.findByMemberIdAndNameContainingIgnoreCase(memberId, query, Limit.of(20));
    }

    public static final int LIST_PAGE_SIZE = 20;

    // Backs /products (list screen) both for the initial SSR render and the JSON the
    // infinite-scroll JS polls for later pages — one query, one filter/sort pass, sliced
    // in memory. Stock-state filtering and 재고순 sort both need per-variant aggregates
    // (a product has no stock/price of its own, ADR 0008), so this loads every owned
    // variant (with product + option groups/values already fetched) rather than querying
    // the DB per page — the same all-at-once approach ProductController's stock-filter
    // tiles already use. Fine at the scale this app runs at; would need a real paged/
    // aggregate query if a seller's catalog grew into the thousands.
    @Transactional(readOnly = true)
    public ProductListPage listPage(Long memberId, String query, String stock, String sort, int page) {
        int lowStockThreshold = memberService.getLowStockThreshold(memberId);
        List<ProductVariant> variants = productVariantRepository.findAllByMemberIdWithProductAndOptions(memberId);
        Map<Long, List<ProductVariant>> variantsByProductId = variants.stream()
                .collect(Collectors.groupingBy(v -> v.getProduct().getId(), LinkedHashMap::new, Collectors.toList()));

        String normalizedQuery = query == null ? "" : query.trim().toLowerCase();
        List<Product> products = variantsByProductId.values().stream()
                .map(vs -> vs.get(0).getProduct())
                .filter(p -> normalizedQuery.isEmpty() || p.getName().toLowerCase().contains(normalizedQuery))
                .filter(p -> matchesStockFilter(stock, variantsByProductId.get(p.getId()), lowStockThreshold))
                .collect(Collectors.toCollection(ArrayList::new));

        // Ties (e.g. several products created in the same instant, or all sitting at 0 stock)
        // need a deterministic tiebreaker — otherwise re-running this sort on every page
        // request could place the same product on two pages, or skip it, since List.sort
        // is stable over the pre-sort order but that order itself has no guaranteed identity.
        // "재고 적은순" is the default (matches "stock", null, or anything unrecognized) —
        // the list screen's primary job is surfacing what needs restocking.
        Comparator<Product> comparator = switch (sort == null ? "" : sort) {
            case "latest" -> Comparator.comparing(Product::getCreatedAt, Comparator.reverseOrder());
            case "name" -> Comparator.comparing(Product::getName);
            default -> Comparator.comparingInt((Product p) -> totalStock(variantsByProductId.get(p.getId())));
        };
        products.sort(comparator.thenComparing(Product::getId, Comparator.reverseOrder()));

        int safePage = Math.max(page, 0);
        int fromIndex = Math.min(safePage * LIST_PAGE_SIZE, products.size());
        int toIndex = Math.min(fromIndex + LIST_PAGE_SIZE, products.size());
        List<ProductListItem> items = products.subList(fromIndex, toIndex).stream()
                .map(p -> toListItem(p, variantsByProductId.get(p.getId()), lowStockThreshold))
                .toList();

        return new ProductListPage(items, products.size(), toIndex < products.size());
    }

    // "low" mirrors the dashboard summary tile's definition — isLowStock() already includes
    // zero-stock variants, so a product with a sold-out combo shows up under 재고부족 too.
    // "out" means every variant is at 0 (totalStock == 0 iff every non-negative addend is 0),
    // not merely "has a sold-out combo" — a product with plenty of stock elsewhere isn't 품절.
    private boolean matchesStockFilter(String stock, List<ProductVariant> variants, int lowStockThreshold) {
        if ("out".equals(stock)) {
            return totalStock(variants) == 0;
        }
        if ("low".equals(stock)) {
            return variants.stream().anyMatch(v -> v.isLowStock(lowStockThreshold));
        }
        return true;
    }

    private int totalStock(List<ProductVariant> variants) {
        return variants.stream().mapToInt(ProductVariant::getStockQuantity).sum();
    }

    private ProductListItem toListItem(Product product, List<ProductVariant> variants, int lowStockThreshold) {
        String thumbnailUrl = product.resolveImageUrl();
        int totalStock = totalStock(variants);
        long zeroStockCount = variants.stream().filter(v -> v.getStockQuantity() == 0).count();
        long lowNonZeroCount = variants.stream()
                .filter(v -> v.getStockQuantity() > 0 && v.isLowStock(lowStockThreshold))
                .count();

        String stockStyle;
        String stockLabel;
        String warningLabel;
        if (totalStock == 0) {
            // Every variant is at 0 — the whole product reads as sold out, not "low".
            stockStyle = "out";
            stockLabel = "품절";
            warningLabel = null;
        } else if (lowNonZeroCount > 0) {
            // At least one combo is running low (but not dead) — the most urgent state,
            // so it takes priority over a merely-sold-out combo elsewhere on the product.
            stockStyle = "low";
            stockLabel = "재고 " + totalStock;
            warningLabel = "임박 " + lowNonZeroCount + "옵션";
        } else if (zeroStockCount > 0) {
            // Healthy overall, but a specific combo is sold out — worth flagging without
            // alarming over the product as a whole (stays green).
            stockStyle = "ok";
            stockLabel = "재고 " + totalStock;
            warningLabel = "품절 " + zeroStockCount + "옵션";
        } else {
            stockStyle = "ok";
            stockLabel = "재고 " + totalStock;
            warningLabel = null;
        }
        String channelLabel = product.getExternalChannelType() != null ? product.getExternalChannelType().getLabel() : null;
        return new ProductListItem(product.getId(), product.getName(), thumbnailUrl,
                optionSummary(variants), ProductVariant.formatPriceRange(variants), stockLabel, stockStyle, warningLabel, channelLabel);
    }

    // "N옵션" total is the product's actual variant count (= the cartesian product of every
    // group's values, by construction at registration/import time) — not recomputed from the
    // per-group counts, so it stays correct even if that invariant ever loosens.
    private String optionSummary(List<ProductVariant> variants) {
        Map<OptionGroup, LinkedHashSet<String>> valuesByGroup = new LinkedHashMap<>();
        for (ProductVariant variant : variants) {
            for (OptionValue optionValue : variant.getOptionValues()) {
                valuesByGroup.computeIfAbsent(optionValue.getOptionGroup(), g -> new LinkedHashSet<>()).add(optionValue.getValue());
            }
        }
        if (valuesByGroup.isEmpty()) {
            return "";
        }
        List<OptionGroup> groups = valuesByGroup.keySet().stream()
                .sorted(Comparator.comparing(OptionGroup::getId))
                .toList();
        List<String> segments = new ArrayList<>();
        for (OptionGroup group : groups) {
            segments.add(summarizeOptionGroup(group.getName(), new ArrayList<>(valuesByGroup.get(group))));
        }
        if (groups.size() >= 2) {
            segments.add(variants.size() + "옵션");
        }
        return String.join(" · ", segments);
    }

    // "색상"/"컬러" groups summarize as a count ("2색") to match the list-screen mockup;
    // every other group lists its values literally while there are few, or falls back to a
    // count once there are enough that spelling them all out would get noisy.
    private String summarizeOptionGroup(String groupName, List<String> values) {
        if ("색상".equals(groupName) || "컬러".equals(groupName)) {
            return values.size() + "색";
        }
        if (values.size() <= 4) {
            return String.join(",", values);
        }
        return values.size() + groupName;
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
        int expectedCombinationCount = combinationCount(optionGroups.stream().map(g -> g.values().size()).toList());
        List<Integer> initialStocks = parseStocks(form.getStocksJson(), expectedCombinationCount);
        List<BigDecimal> prices = parsePrices(form.getPricesJson(), expectedCombinationCount, form.getPrice());
        List<String> barcodes = parseBarcodes(form.getBarcodesJson(), expectedCombinationCount);

        Member member = memberRepository.getReferenceById(memberId);
        Product product = productRepository.save(new Product(member, form.getName()));
        product.updateCostPrice(form.getCostPrice());

        List<List<OptionValue>> groupsOfValues = new ArrayList<>();
        for (ParsedOptionGroup group : optionGroups) {
            groupsOfValues.add(saveOptionGroup(product, group.name(), group.values()));
        }

        List<Set<OptionValue>> combinations = cartesianProduct(groupsOfValues);
        // Sequence continues from however many barcodes this member already has, so codes
        // stay unique per member even across separate registrations (ADR-less convention —
        // see generateAutoBarcode()).
        long autoBarcodeSequence = productVariantRepository.countByMemberIdAndBarcodeIsNotNull(memberId);
        // Inlined rather than delegated to MovementService.record() — MovementService already
        // depends on ProductService (for ownership checks), so calling back here would be
        // circular. Kept in sync by hand: adjustStock() + save(Movement) here must mirror
        // record()'s INBOUND branch exactly.
        for (int i = 0; i < combinations.size(); i++) {
            ProductVariant variant = productVariantRepository.save(new ProductVariant(product, combinations.get(i), prices.get(i)));
            int initialStock = initialStocks.get(i);
            if (initialStock > 0) {
                variant.adjustStock(initialStock);
                movementRepository.save(new Movement(variant, null, MovementType.INBOUND, initialStock, INITIAL_STOCK_MEMO));
            }
            if (barcodes.get(i) != null && !barcodes.get(i).isBlank()) {
                variant.assignBarcode(barcodes.get(i));
            } else if (form.isAutoGenerateBarcode()) {
                String code;
                do {
                    autoBarcodeSequence++;
                    code = generateAutoBarcode(memberId, autoBarcodeSequence);
                } while (productVariantRepository.existsByMemberIdAndBarcode(memberId, code));
                variant.assignBarcode(code);
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

    // The edit screen reuses the registration form (same photo/name/price/costPrice/
    // optionGroups/pricesJson/autoGenerateBarcode shape as create()) — see ProductEditForm.
    // The one thing create() never has to deal with is EXISTING variants: an option-group
    // edit here must preserve every existing ProductVariant's stock/barcode/movement history
    // unless the seller genuinely removes that combination, and even then only when it's safe
    // (see the movement guard below). Order of operations mirrors create()'s discipline —
    // every validation (photo, cross-tenant group ids, the movement guard, combination-count,
    // price shape) runs before any row is written, so a rejected edit never leaves a
    // half-applied group/value/variant behind even within the caller's own transaction.
    @Transactional
    public Product update(Long memberId, Long productId, ProductEditForm form) {
        Product product = getOwned(memberId, productId);
        boolean hasPhoto = hasPhoto(form.getPhoto());
        if (hasPhoto) {
            validatePhoto(form.getPhoto());
        }

        List<ParsedEditGroup> parsedGroups = parseOptionGroupsForEdit(form.getOptionGroups());
        List<ProductVariant> existingVariants = productVariantRepository.findAllByProductIdWithOptions(productId);

        // Entity references + per-group value-text maps, both keyed by existing group id —
        // built once from the already-fetched variants (no extra queries) and reused both
        // for validation and, once validation passes, for reuse-or-create resolution below.
        Map<Long, OptionGroup> existingGroupsById = new LinkedHashMap<>();
        Map<Long, Map<String, OptionValue>> existingValuesByGroupId = new LinkedHashMap<>();
        for (ProductVariant variant : existingVariants) {
            for (OptionValue optionValue : variant.getOptionValues()) {
                OptionGroup group = optionValue.getOptionGroup();
                existingGroupsById.putIfAbsent(group.getId(), group);
                existingValuesByGroupId.computeIfAbsent(group.getId(), g -> new LinkedHashMap<>())
                        .put(optionValue.getValue(), optionValue);
            }
        }

        // A submitted group id that isn't one of this product's own groups is either a bug
        // in the client or a tampered request (e.g. another product's group id) — reject
        // before any resolution, same spirit as the old variantId ownership check.
        for (ParsedEditGroup group : parsedGroups) {
            if (group.existingGroupId() != null && !existingGroupsById.containsKey(group.existingGroupId())) {
                throw new InvalidPriceDataException("잘못된 옵션 정보입니다.");
            }
        }

        // ---- Validation phase: which existing variants would this edit drop? (read-only) ----
        Map<Long, Set<String>> submittedValueTextsByGroupId = new LinkedHashMap<>();
        for (ParsedEditGroup group : parsedGroups) {
            if (group.existingGroupId() != null) {
                submittedValueTextsByGroupId.put(group.existingGroupId(), new LinkedHashSet<>(group.values()));
            }
        }
        List<ProductVariant> variantsToDelete = new ArrayList<>();
        List<String> blockedLabels = new ArrayList<>();
        for (ProductVariant variant : existingVariants) {
            boolean stillValid = variant.getOptionValues().stream().allMatch(ov -> {
                Set<String> allowed = submittedValueTextsByGroupId.get(ov.getOptionGroup().getId());
                return allowed != null && allowed.contains(ov.getValue());
            });
            if (stillValid) {
                continue;
            }
            // 재고>0이면 반드시 최소 한 건의 Movement가 있다(ADR 0006) — 존재 여부만 확인해도
            // "재고가 있는 옵션"을 정확히 잡아내면서, 지금은 0이지만 판매·조정 이력이 있는
            // 조합까지 함께 보호한다(그 이력을 가진 variant는 FK 제약상 물리적으로 삭제 불가).
            if (movementRepository.existsByVariant_Id(variant.getId())) {
                blockedLabels.add(variant.getDisplayName());
            } else {
                variantsToDelete.add(variant);
            }
        }
        if (!blockedLabels.isEmpty()) {
            throw new VariantHasMovementsException("재고 기록이 있는 옵션은 삭제할 수 없어요: " + String.join(", ", blockedLabels));
        }

        // combinationCount()/parsePrices() only need sizes at this point, not resolved
        // entities — validating the price payload's shape here, before any group/value row
        // is written, keeps the "nothing persists on rejection" guarantee intact.
        int expectedCombinationCount = combinationCount(parsedGroups.stream().map(g -> g.values().size()).toList());
        List<BigDecimal> prices = parsePrices(form.getPricesJson(), expectedCombinationCount, form.getPrice());

        // ---- Mutation phase — every validation above has passed. ----
        product.updateInfo(form.getName());
        product.updateCostPrice(form.getCostPrice());

        List<List<OptionValue>> groupsOfValues = new ArrayList<>();
        for (ParsedEditGroup group : parsedGroups) {
            OptionGroup resolvedGroup;
            Map<String, OptionValue> existingByText;
            if (group.existingGroupId() != null) {
                resolvedGroup = existingGroupsById.get(group.existingGroupId());
                if (!resolvedGroup.getName().equals(group.name())) {
                    resolvedGroup.updateName(group.name());
                }
                existingByText = existingValuesByGroupId.getOrDefault(group.existingGroupId(), Map.of());
            } else {
                resolvedGroup = optionGroupRepository.save(new OptionGroup(product, group.name()));
                existingByText = Map.of();
            }
            List<OptionValue> values = new ArrayList<>();
            for (String text : group.values()) {
                OptionValue value = existingByText.get(text);
                values.add(value != null ? value : optionValueRepository.save(new OptionValue(resolvedGroup, text)));
            }
            groupsOfValues.add(values);
        }

        List<Set<OptionValue>> newCombos = cartesianProduct(groupsOfValues);

        // Subset-extension: each surviving existing variant claims the first not-yet-claimed
        // combo whose value set is a superset of its own — trivially itself if nothing about
        // its groups changed, or the combo it "expands into" if a new group was added. Two
        // existing variants can never compete for the same combo: they differ in at least one
        // already-existing group's value, and a combo carries exactly one value per group.
        boolean[] claimed = new boolean[newCombos.size()];
        Map<Integer, ProductVariant> variantByComboIndex = new LinkedHashMap<>();
        for (ProductVariant variant : existingVariants) {
            if (variantsToDelete.contains(variant)) {
                continue;
            }
            int matchedIndex = -1;
            for (int i = 0; i < newCombos.size(); i++) {
                if (!claimed[i] && newCombos.get(i).containsAll(variant.getOptionValues())) {
                    matchedIndex = i;
                    break;
                }
            }
            if (matchedIndex == -1) {
                // Guarded against above (every value this variant has was confirmed present
                // in its group's submitted text set), so the cartesian product necessarily
                // contains a combo matching it — this would only fire on a logic error.
                throw new IllegalStateException("옵션 조합 매칭에 실패했습니다: " + variant.getDisplayName());
            }
            claimed[matchedIndex] = true;
            variantByComboIndex.put(matchedIndex, variant);
        }

        long autoBarcodeSequence = productVariantRepository.countByMemberIdAndBarcodeIsNotNull(memberId);
        for (int i = 0; i < newCombos.size(); i++) {
            ProductVariant existing = variantByComboIndex.get(i);
            if (existing != null) {
                existing.replaceOptionValues(newCombos.get(i));
                existing.updatePrice(prices.get(i));
                continue;
            }
            // A combo with no matching existing variant is brand new — always starts at 0
            // stock (재고는 상세 화면의 Movement 조정에서만), barcode only if requested.
            ProductVariant created = productVariantRepository.save(new ProductVariant(product, newCombos.get(i), prices.get(i)));
            if (form.isAutoGenerateBarcode()) {
                String code;
                do {
                    autoBarcodeSequence++;
                    code = generateAutoBarcode(memberId, autoBarcodeSequence);
                } while (productVariantRepository.existsByMemberIdAndBarcode(memberId, code));
                created.assignBarcode(code);
            }
        }

        for (ProductVariant variant : variantsToDelete) {
            productVariantRepository.delete(variant);
        }
        // Flush before deleting OptionValue rows below — without it, Hibernate could still
        // have the now-stale product_variant_option_values join rows (from the deletes above
        // and from replaceOptionValues() dropping a value) queued rather than written, and
        // the explicit OptionValue delete would then violate that join table's FK.
        productVariantRepository.flush();

        Set<Long> submittedGroupIds = parsedGroups.stream()
                .map(ParsedEditGroup::existingGroupId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        for (Map.Entry<Long, OptionGroup> entry : existingGroupsById.entrySet()) {
            Long existingGroupId = entry.getKey();
            Map<String, OptionValue> existingValues = existingValuesByGroupId.getOrDefault(existingGroupId, Map.of());
            if (!submittedGroupIds.contains(existingGroupId)) {
                existingValues.values().forEach(optionValueRepository::delete);
                optionGroupRepository.delete(entry.getValue());
                continue;
            }
            Set<String> stillUsed = submittedValueTextsByGroupId.get(existingGroupId);
            for (OptionValue optionValue : existingValues.values()) {
                if (!stillUsed.contains(optionValue.getValue())) {
                    optionValueRepository.delete(optionValue);
                }
            }
        }

        // Store the photo last, after every other write — file writes aren't transactional,
        // so an earlier failure must not leave an orphaned (written but unreferenced) photo.
        if (hasPhoto) {
            String oldPhotoKey = product.getPhotoKey();
            product.updatePhotoKey(storePhoto(form.getPhoto()));
            if (oldPhotoKey != null) {
                fileStorage.delete(oldPhotoKey);
            }
        }

        return product;
    }

    private record ParsedEditGroup(Long existingGroupId, String name, List<String> values) {
    }

    private List<ParsedEditGroup> parseOptionGroupsForEdit(List<ProductEditForm.OptionGroupInput> inputs) {
        List<ParsedEditGroup> groups = new ArrayList<>();
        for (ProductEditForm.OptionGroupInput input : inputs) {
            String name = input.getName() == null ? "" : input.getName().trim();
            List<String> values = parseDistinctValues(input.getValuesText());
            if (name.isEmpty() || values.isEmpty()) {
                continue;
            }
            groups.add(new ParsedEditGroup(input.getId(), name, values));
        }
        return groups;
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
    // "compute then compare" check would ever see it. Takes plain sizes (not ParsedOptionGroup)
    // so both create() (fresh groups) and update() (a mix of reused/new groups, already
    // resolved to entities by the time this runs) can share it.
    private int combinationCount(List<Integer> groupSizes) {
        int count = 1;
        for (int size : groupSizes) {
            count *= size;
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

    // Blank/missing pricesJson defaults every combination to the base price. Per-value
    // surcharges only ever exist client-side (typed into the registration screen's JS) —
    // the server has no way to reconstruct a per-combo price from nothing, so a caller that
    // skips pricesJson entirely gets uniform base pricing across every combination, not
    // base+surcharge (matches parseStocks()'s blank-defaults precedent, but note this one
    // can't "know" surcharges the way stock defaults to zero).
    private List<BigDecimal> parsePrices(String pricesJson, int expectedCount, BigDecimal basePrice) {
        if (pricesJson == null || pricesJson.isBlank()) {
            return new ArrayList<>(Collections.nCopies(expectedCount, basePrice));
        }
        List<BigDecimal> prices;
        try {
            prices = objectMapper.readValue(pricesJson, new TypeReference<List<BigDecimal>>() { });
        } catch (JacksonException e) {
            throw new InvalidPriceDataException("가격 데이터 형식이 올바르지 않습니다.");
        }
        if (prices.size() != expectedCount) {
            throw new InvalidPriceDataException("가격 입력 개수가 옵션 조합 개수와 일치하지 않습니다.");
        }
        if (prices.stream().anyMatch(price -> price == null || price.signum() < 0)) {
            throw new InvalidPriceDataException("가격은 0 이상의 숫자여야 합니다.");
        }
        return prices;
    }

    // Blank/missing barcodesJson means "not generated" — every combination gets no barcode
    // (nulls), matching stocksJson/pricesJson's blank-defaults precedent. Unlike those two,
    // barcode assignment stays optional even when the array is present in shape: this only
    // rejects a wrong-length array or duplicate codes within it, not blank/null elements.
    private List<String> parseBarcodes(String barcodesJson, int expectedCount) {
        if (barcodesJson == null || barcodesJson.isBlank()) {
            return new ArrayList<>(Collections.nCopies(expectedCount, null));
        }
        List<String> barcodes;
        try {
            barcodes = objectMapper.readValue(barcodesJson, new TypeReference<List<String>>() { });
        } catch (JacksonException e) {
            throw new InvalidBarcodeDataException("바코드 데이터 형식이 올바르지 않습니다.");
        }
        if (barcodes.size() != expectedCount) {
            throw new InvalidBarcodeDataException("바코드 입력 개수가 옵션 조합 개수와 일치하지 않습니다.");
        }
        List<String> nonBlank = barcodes.stream().filter(b -> b != null && !b.isBlank()).toList();
        if (nonBlank.stream().distinct().count() != nonBlank.size()) {
            throw new InvalidBarcodeDataException("바코드는 중복될 수 없습니다.");
        }
        return barcodes;
    }

    // Sequential, per-member code: "WJG" + memberId + 6-digit sequence number. The caller
    // re-derives the exact next sequence via existsByMemberIdAndBarcode rather than trusting
    // this to be collision-free outright, since a deleted/reassigned variant could leave the
    // running count ahead of the true next-free number.
    private String generateAutoBarcode(Long memberId, long sequence) {
        return "WJG" + memberId + String.format("%06d", sequence);
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
