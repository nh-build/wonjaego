package com.wonjaego.product;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductVariantEditForm {

    // Nullable — a variant can be left without a SKU indefinitely.
    private String sku;

    @Min(0)
    private Integer lowStockThreshold;

    public static ProductVariantEditForm from(ProductVariant variant) {
        ProductVariantEditForm form = new ProductVariantEditForm();
        form.setSku(variant.getSku());
        form.setLowStockThreshold(variant.getLowStockThreshold());
        return form;
    }
}
