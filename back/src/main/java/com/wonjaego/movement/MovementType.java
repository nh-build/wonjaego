package com.wonjaego.movement;

public enum MovementType {

    INBOUND("입고"),
    SALE("판매"),
    RETURN("반품"),
    EXCHANGE("교환"),
    // ADJUSTMENT_IN/OUT are two constants (not one bidirectional ADJUSTMENT) so that sign
    // stays inferable from type alone everywhere, matching every other constant here —
    // see MovementService.signedQuantity().
    ADJUSTMENT_IN("조정"),
    ADJUSTMENT_OUT("조정"),
    DISPOSAL("폐기"),
    EXCHANGE_OUT("교환출고");

    private final String label;

    MovementType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
