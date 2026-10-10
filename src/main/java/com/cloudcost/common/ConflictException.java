package com.cloudcost.common;

/** The request conflicts with the current state, for example a duplicate import. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
