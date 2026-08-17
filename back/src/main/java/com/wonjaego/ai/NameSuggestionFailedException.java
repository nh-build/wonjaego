package com.wonjaego.ai;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_GATEWAY)
public class NameSuggestionFailedException extends RuntimeException {

    public NameSuggestionFailedException(String message) {
        super(message);
    }

    public NameSuggestionFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
