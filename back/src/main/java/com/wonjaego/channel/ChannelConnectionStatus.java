package com.wonjaego.channel;

// A ChannelCredential row's connection state, independent of whether it still holds live
// keys. Disconnecting clears the encrypted keys but keeps the row (and its role/import
// history) so reconnecting doesn't lose a seller's prior role choice.
public enum ChannelConnectionStatus {

    NOT_CONNECTED("미연동"),
    CONNECTED("연동됨");

    private final String label;

    ChannelConnectionStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
