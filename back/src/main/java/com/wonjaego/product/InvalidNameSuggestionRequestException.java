package com.wonjaego.product;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidNameSuggestionRequestException extends RuntimeException {

    public InvalidNameSuggestionRequestException(String message) {
        super(message);
    }
}
