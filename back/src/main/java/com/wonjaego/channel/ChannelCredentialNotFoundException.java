package com.wonjaego.channel;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ChannelCredentialNotFoundException extends RuntimeException {

    public ChannelCredentialNotFoundException(ChannelType channelType) {
        super("연동된 채널 키를 찾을 수 없습니다: " + channelType);
    }
}
