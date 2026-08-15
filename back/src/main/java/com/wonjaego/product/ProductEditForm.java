package com.wonjaego.product;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductEditForm {

    @NotBlank
    private String name;

    @NotBlank
    private String sku;

    @NotNull
    private BigDecimal price;

    @Min(0)
    private Integer lowStockThreshold;

    public static ProductEditForm from(Product product) {
        ProductEditForm form = new ProductEditForm();
        form.setName(product.getName());
        form.setSku(product.getSku());
        form.setPrice(product.getPrice());
        form.setLowStockThreshold(product.getLowStockThreshold());
        return form;
    }
}
