package com.wonjaego.channel;

public class SalesChannelHasMovementsException extends RuntimeException {

    public SalesChannelHasMovementsException(String channelName) {
        super("재고 기록이 있어 삭제할 수 없습니다: " + channelName);
    }
}
