package com.wonjaego.movement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StockMovementForm {

    @NotNull
    private Long productId;

    @NotNull
    private MovementType type;

    private String memo;

    @Valid
    private List<QuantityEntry> entries = new ArrayList<>();

    @Getter
    @Setter
    public static class QuantityEntry {

        @NotNull
        private Long variantId;

        @NotNull
        @Min(0)
        private Integer quantity;
    }
}
