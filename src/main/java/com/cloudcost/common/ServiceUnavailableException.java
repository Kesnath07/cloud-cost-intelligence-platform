package com.cloudcost.common;

/**
 * A dependency required for the request (such as an external API) is disabled or failing.
 * The message must be actionable and must not contain credentials or internal details.
 */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
