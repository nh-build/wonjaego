package com.wonjaego.ai;

public enum NamingConcept {
    SIMPLE("심플"),
    LOVELY("러블리"),
    SEXY("섹시"),
    CASUAL("캐주얼");

    private final String label;

    NamingConcept(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
