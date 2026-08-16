package com.wonjaego.product;

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

    @NotNull
    private BigDecimal price;

    public static ProductEditForm from(Product product) {
        ProductEditForm form = new ProductEditForm();
        form.setName(product.getName());
        form.setPrice(product.getPrice());
        return form;
    }
}
