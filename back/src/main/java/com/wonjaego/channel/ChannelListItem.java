package com.wonjaego.channel;

// One row on the /channels connect-list screen. credentialId is null unless connected —
// only a connected channel has a "관리" link (screen 2 needs the credential's id).
public record ChannelListItem(ChannelType channelType, boolean connected, Long credentialId) {
}
