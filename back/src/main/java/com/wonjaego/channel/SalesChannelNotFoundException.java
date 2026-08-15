package com.wonjaego.channel;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class SalesChannelNotFoundException extends RuntimeException {

    public SalesChannelNotFoundException(Long id) {
        super("판매 채널을 찾을 수 없습니다: " + id);
    }
}
