package com.wonjaego.channel;

public class DuplicateChannelNameException extends RuntimeException {

    public DuplicateChannelNameException(String name) {
        super("이미 사용 중인 채널 이름입니다: " + name);
    }
}
