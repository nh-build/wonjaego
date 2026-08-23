package com.wonjaego.product;

public class InvalidStockDataException extends RuntimeException {

    public InvalidStockDataException(String message) {
        super(message);
    }
}
