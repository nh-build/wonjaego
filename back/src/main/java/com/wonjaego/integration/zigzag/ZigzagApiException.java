package com.wonjaego.integration.zigzag;

public class ZigzagApiException extends RuntimeException {

    public ZigzagApiException(String message) {
        super(message);
    }

    public ZigzagApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
