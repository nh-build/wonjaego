package com.wonjaego.product;

public class InvalidBarcodeDataException extends RuntimeException {

    public InvalidBarcodeDataException(String message) {
        super(message);
    }
}
