package com.wonjaego.member;

public class InvalidLowStockThresholdException extends RuntimeException {

    public InvalidLowStockThresholdException(String message) {
        super(message);
    }
}
