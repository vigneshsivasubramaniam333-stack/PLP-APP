package com.plp.program.integration.los;

/** Raised when the outbound call from PLP to the LOS anchor onboarding integration surface fails. */
public class LosIntegrationException extends RuntimeException {

    private final Integer httpStatus;

    public LosIntegrationException(String message) {
        this(message, null, null);
    }

    public LosIntegrationException(String message, Throwable cause) {
        this(message, cause, null);
    }

    public LosIntegrationException(String message, Throwable cause, Integer httpStatus) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }
}
