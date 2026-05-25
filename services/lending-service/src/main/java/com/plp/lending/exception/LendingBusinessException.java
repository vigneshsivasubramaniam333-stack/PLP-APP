package com.plp.lending.exception;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

/**
 * Business rule failure with an HTTP status suitable for clients (not a generic 500).
 */
public class LendingBusinessException extends ResponseStatusException {

    public LendingBusinessException(HttpStatusCode status, String reason) {
        super(status, reason);
    }
}
