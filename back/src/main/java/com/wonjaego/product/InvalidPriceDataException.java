package com.wonjaego.product;

public class InvalidPriceDataException extends RuntimeException {

    public InvalidPriceDataException(String message) {
        super(message);
    }
}
