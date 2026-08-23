package com.wonjaego.channel;

// The fixed set of platforms selectable on the 채널 연동 screen. Distinct from SalesChannel
// (a seller's free-text channel tag, ADR 0005) — this enumerates platforms wonjaego knows how
// to talk to via API, not channels a seller has manually registered.
public enum ChannelType {

    ZIGZAG("지그재그", true),
    SMARTSTORE("스마트스토어", false),
    COUPANG("쿠팡", false),
    ABLY("에이블리", false);

    private final String label;
    private final boolean integrationSupported;

    ChannelType(String label, boolean integrationSupported) {
        this.label = label;
        this.integrationSupported = integrationSupported;
    }

    public String getLabel() {
        return label;
    }

    // Only ZIGZAG has a real API integration today — the others are selectable but show a
    // "준비중" notice instead of a key-entry form.
    public boolean isIntegrationSupported() {
        return integrationSupported;
    }
}
