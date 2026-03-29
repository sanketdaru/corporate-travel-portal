package com.corporate.travel.bff.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class DelegationNotFoundException extends RuntimeException {

    public DelegationNotFoundException(String delegationId) {
        super("Delegation not found: " + delegationId);
    }
}
