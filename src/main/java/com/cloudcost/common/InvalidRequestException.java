package com.cloudcost.common;

/**
 * A request that cannot be processed because of invalid input. Messages are shown to
 * users and must therefore never contain internal details.
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
