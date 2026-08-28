package com.wonjaego.channel;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidChannelOperationException extends RuntimeException {

    public InvalidChannelOperationException(String message) {
        super(message);
    }
}
