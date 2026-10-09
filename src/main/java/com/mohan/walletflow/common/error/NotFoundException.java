package com.mohan.walletflow.common.error;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {

    public NotFoundException(String resource, Object id) {
        super(HttpStatus.NOT_FOUND, "not-found", resource + " " + id + " was not found");
    }
}
