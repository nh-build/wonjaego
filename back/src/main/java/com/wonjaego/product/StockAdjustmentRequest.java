package com.wonjaego.product;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

// Body of POST /products/{id}/stock-adjustments — the product-detail screen's "변경사항
// 저장" bar. type is a raw string (not MovementType) so the controller can reject an
// unrecognized value with a normal 400 instead of a Jackson deserialization 400 with a
// less useful message.
@Getter
@Setter
public class StockAdjustmentRequest {

    private List<Entry> entries = new ArrayList<>();

    @Getter
    @Setter
    public static class Entry {
        private Long variantId;
        private String type;
        private int quantity;
    }
}
