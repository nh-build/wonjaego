package com.wonjaego.product;

// Thrown when a product-edit's option-composition change would drop an existing
// ProductVariant that still has movement history (stock>0 always implies at least one
// movement, per ADR 0006) — deleting it would violate Movement.product_variant_id's NOT
// NULL FK, so this is a hard block, not a soft warning the caller can override.
public class VariantHasMovementsException extends RuntimeException {

    public VariantHasMovementsException(String message) {
        super(message);
    }
}
