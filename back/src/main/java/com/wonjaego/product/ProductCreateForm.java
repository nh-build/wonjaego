package com.wonjaego.product;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductCreateForm {

    @NotBlank
    private String name;

    @NotBlank
    private String sku;

    @NotNull
    private BigDecimal price;

    @NotNull
    @Min(0)
    private Integer stockQuantity;

    @Min(0)
    private Integer lowStockThreshold;
}
