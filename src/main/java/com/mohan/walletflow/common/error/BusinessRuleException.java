package com.mohan.walletflow.common.error;

import org.springframework.http.HttpStatus;

/** A syntactically valid request that violates a business rule (HTTP 422). */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String code, String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }
}
