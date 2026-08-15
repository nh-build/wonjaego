package com.wonjaego.movement;

public enum MovementType {

    INBOUND("입고"),
    SALE("판매"),
    RETURN("반품");

    private final String label;

    MovementType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
