package com.wonjaego.product;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class ProductEditForm {

    @NotBlank
    private String name;

    // Optional — submitting without a file keeps the product's existing photo untouched.
    private MultipartFile photo;

    public static ProductEditForm from(Product product) {
        ProductEditForm form = new ProductEditForm();
        form.setName(product.getName());
        return form;
    }
}
