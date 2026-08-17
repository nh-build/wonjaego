package com.wonjaego.product;

public class InvalidPhotoException extends RuntimeException {

    public InvalidPhotoException(String message) {
        super(message);
    }
}
