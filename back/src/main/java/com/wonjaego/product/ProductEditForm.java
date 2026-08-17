package com.wonjaego.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class ProductEditForm {

    @NotBlank
    private String name;

    @NotNull
    private BigDecimal price;

    // Optional — submitting without a file keeps the product's existing photo untouched.
    private MultipartFile photo;

    public static ProductEditForm from(Product product) {
        ProductEditForm form = new ProductEditForm();
        form.setName(product.getName());
        form.setPrice(product.getPrice());
        return form;
    }
}
