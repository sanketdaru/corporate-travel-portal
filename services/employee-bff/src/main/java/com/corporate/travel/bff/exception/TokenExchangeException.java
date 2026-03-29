package com.corporate.travel.bff.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_GATEWAY)
public class TokenExchangeException extends RuntimeException {

    public TokenExchangeException(String message) {
        super(message);
    }

    public TokenExchangeException(String message, Throwable cause) {
        super(message, cause);
    }
}
