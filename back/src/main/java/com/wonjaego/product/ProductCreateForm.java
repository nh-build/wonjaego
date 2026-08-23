package com.wonjaego.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class ProductCreateForm {

    @NotBlank
    private String name;

    // 기본가 — the price used directly when there are no option combinations, and as the
    // base that each combo's price is calculated from (base + matching option-value
    // surcharges) on the registration screen (ADR 0008).
    @NotNull
    @DecimalMin(value = "0", message = "기본가는 0 이상이어야 합니다.")
    private BigDecimal price;

    private MultipartFile photo;

    // Submitted as indexed request params (optionGroups[0].name, optionGroups[0].valuesText,
    // optionGroups[1].name, ...) by dynamically added/removed rows on the registration
    // screen — Spring's binder auto-grows this list from those indexed params, so it starts
    // empty rather than pre-populated with fixed slots.
    @Size(max = 20, message = "옵션은 최대 20개까지 추가할 수 있습니다.")
    private List<OptionGroupInput> optionGroups = new ArrayList<>();

    // JSON array of non-negative integers, one per generated combination, in the same order
    // the combinations are generated server-side (first option group outermost, matching how
    // the registration screen renders and submits them). Blank/null is treated as "all zero"
    // for however many combinations end up being generated.
    private String stocksJson;

    // JSON array of non-negative prices, one per generated combination, in the same order
    // as stocksJson — the registration screen auto-calculates each combo's price (base +
    // matching option-value surcharges) but lets the seller override any of them before
    // submit. Blank/null defaults every combination to the base price (ADR 0008).
    private String pricesJson;

    @Getter
    @Setter
    public static class OptionGroupInput {

        // Both name and valuesText must be filled for this row to become a real
        // OptionGroup — a row left blank (e.g. added then never filled in) is silently
        // ignored rather than rejected, since it just means "not used".
        private String name;
        private String valuesText;
    }
}
