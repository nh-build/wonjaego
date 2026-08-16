package com.wonjaego.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductCreateForm {

    @NotBlank
    private String name;

    @NotNull
    private BigDecimal price;

    // Pre-populated with empty slots (rather than left empty) so the registration form can
    // render a fixed number of th:field-bound rows — Thymeleaf/Spring read indexed nested
    // properties by index when displaying a form, and an empty list has no index 0 to read.
    public static final int MAX_OPTION_GROUPS = 5;

    private List<OptionGroupInput> optionGroups = emptyOptionGroupSlots();

    private static List<OptionGroupInput> emptyOptionGroupSlots() {
        List<OptionGroupInput> slots = new ArrayList<>();
        for (int i = 0; i < MAX_OPTION_GROUPS; i++) {
            slots.add(new OptionGroupInput());
        }
        return slots;
    }

    @Getter
    @Setter
    public static class OptionGroupInput {

        // Both name and valuesText must be filled for this slot to become a real
        // OptionGroup — a slot left blank (e.g. an unused row in a fixed-size form) is
        // silently ignored rather than rejected, since it just means "not used".
        private String name;
        private String valuesText;
    }
}
