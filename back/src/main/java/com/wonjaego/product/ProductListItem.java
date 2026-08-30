package com.wonjaego.product;

// Backs one card on the /products list screen. stockStyle is one of "ok"/"low"/"out",
// driving the badge color client-side — kept as a plain string rather than an enum since
// the template only ever uses it as a CSS class suffix. warningLabel is the small red
// line under the badge ("임박 2옵션"/"품절 1옵션"), null when nothing needs flagging.
public record ProductListItem(Long id, String name, String thumbnailUrl, String optionSummary,
                               String priceRange, String stockLabel, String stockStyle,
                               String warningLabel, String channelLabel) {
}
