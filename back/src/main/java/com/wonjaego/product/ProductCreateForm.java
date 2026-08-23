package com.wonjaego.product;

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

    @NotNull
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
