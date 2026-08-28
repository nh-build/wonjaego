package com.wonjaego.channel;

// What a connected ChannelType is used for. PRODUCT_SOURCE channels are where products (and
// their stock/price) get imported from; ORDER_SYNC channels only ever contribute order/
// exchange/refund events against stock already known from a product-source channel — so a
// seller is expected to have at most one PRODUCT_SOURCE channel connected at a time (the
// 판매채널 screen's own footnote makes this explicit). A ChannelType with no role at all
// (see ChannelType.getRole()) means wonjaego doesn't yet know how it would integrate.
public enum ChannelRole {

    PRODUCT_SOURCE("상품 소스"),
    ORDER_SYNC("주문 연동");

    private final String label;

    ChannelRole(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
