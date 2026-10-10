package com.cloudcost.ingestion;

import com.cloudcost.common.ConflictException;

public class DuplicateImportException extends ConflictException {

    public DuplicateImportException(String message) {
        super(message);
    }
}
