package com.wonjaego.channel;

import java.time.LocalDateTime;

// The /channels/{id} management screen's view model. canImportProducts is true only for a
// connected ZIGZAG channel with role=PRODUCT_SOURCE — Zigzag import is the only integration
// actually wired up today, so the button must not appear for any other channel/role combo
// even though role itself is a generic per-connection concept.
public record ChannelManageView(Long credentialId, ChannelType channelType, ChannelConnectionStatus status,
                                 ChannelRole role, LocalDateTime connectedAt, boolean canImportProducts,
                                 LocalDateTime lastImportedAt, Integer lastImportedCount) {
}
