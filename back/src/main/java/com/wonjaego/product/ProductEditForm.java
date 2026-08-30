package com.wonjaego.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

// Mirrors ProductCreateForm's shape (same fields, same client-side combo/price mechanism) —
// the edit screen reuses the registration form template rather than a separate simplified
// one. Differences from create: optionGroups carry a stable existing-group id so renamed/
// extended groups keep their ProductVariant rows (see ProductService.update()); there is no
// stocksJson at all (stock changes go through the detail screen's Movement-backed adjuster,
// never this form); autoGenerateBarcode only applies to brand-new combos this edit creates.
@Getter
@Setter
public class ProductEditForm {

    @NotBlank
    private String name;

    private MultipartFile photo;

    // 기본가 — same role as ProductCreateForm.price: the base a new combo's auto-price is
    // computed from (base + matching option-value surcharges) client-side. Product itself
    // stores no price (ADR 0008), so this is prefilled from the existing variants (see
    // from()) purely as a starting point for that formula; it never retroactively reprices
    // already-existing combos (their prices arrive back through pricesJson unchanged unless
    // explicitly edited).
    @NotNull
    @DecimalMin(value = "0", message = "기본가는 0 이상이어야 합니다.")
    private BigDecimal price;

    @DecimalMin(value = "0", message = "원가는 0 이상이어야 합니다.")
    private BigDecimal costPrice;

    @Size(max = 20, message = "옵션은 최대 20개까지 추가할 수 있습니다.")
    private List<OptionGroupInput> optionGroups = new ArrayList<>();

    // Same contract as ProductCreateForm.pricesJson — one price per generated combination,
    // in cartesianProduct() order. Blank/null defaults every combination to the base price.
    private String pricesJson;

    // Applies only to combos this edit newly creates (added option/value) — an existing
    // combo's barcode (or lack of one) is never touched, regardless of this flag.
    private boolean autoGenerateBarcode;

    public static ProductEditForm from(Product product, List<ProductVariant> variants) {
        ProductEditForm form = new ProductEditForm();
        form.setName(product.getName());
        form.setCostPrice(product.getCostPrice());
        form.setPrice(variants.stream().map(ProductVariant::getPrice).min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO));

        Map<OptionGroup, LinkedHashSet<String>> valuesByGroup = new LinkedHashMap<>();
        for (ProductVariant variant : variants) {
            for (OptionValue optionValue : variant.getOptionValues()) {
                valuesByGroup.computeIfAbsent(optionValue.getOptionGroup(), g -> new LinkedHashSet<>()).add(optionValue.getValue());
            }
        }
        List<OptionGroup> groups = valuesByGroup.keySet().stream()
                .sorted(Comparator.comparing(OptionGroup::getId))
                .toList();
        List<OptionGroupInput> groupInputs = new ArrayList<>();
        for (OptionGroup group : groups) {
            OptionGroupInput input = new OptionGroupInput();
            input.setId(group.getId());
            input.setName(group.getName());
            input.setValuesText(String.join(", ", valuesByGroup.get(group)));
            groupInputs.add(input);
        }
        form.setOptionGroups(groupInputs);

        // 옵션이 있는 경우에만 의미 있는 체크 — 바코드는 옵션 조합(variant) 단위로 붙으므로,
        // "이미 전부 바코드가 있다"는 곧 "새로 추가되는 조합도 이어서 자동 발급해달라"는
        // 기존 습관을 그대로 이어가는 합리적인 기본값이다. Product/ProductVariant에는 이
        // 체크박스 상태 자체를 저장하는 필드가 없으므로 매번 이렇게 유도한다.
        form.setAutoGenerateBarcode(!variants.isEmpty() && variants.stream().allMatch(v -> v.getBarcode() != null));

        return form;
    }

    @Getter
    @Setter
    public static class OptionGroupInput {

        // Existing OptionGroup id, or null for a row the seller added during this edit.
        // A non-null id that doesn't belong to this product is rejected by
        // ProductService.update() before anything is mutated.
        private Long id;

        private String name;
        private String valuesText;
    }
}
