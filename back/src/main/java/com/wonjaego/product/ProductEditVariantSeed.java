package com.wonjaego.product;

import java.math.BigDecimal;
import java.util.Map;

// Feeds the edit screen's client-side combo builder (same JS as registration) with each
// existing variant's current state, so it can detect "this combo already exists" (preserve
// its price/stock/barcode display, warn before dropping it) the same way
// ProductService.update() does server-side — by matching existing-group-id -> value-text,
// not by variant id or option-value id (the client never sees those).
public record ProductEditVariantSeed(Long variantId, String optionLabel, Map<Long, String> valueTextByGroupId,
                                      BigDecimal price, int stockQuantity, boolean hasBarcode) {
}
