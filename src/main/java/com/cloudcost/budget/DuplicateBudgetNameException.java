package com.cloudcost.budget;

import com.cloudcost.common.ConflictException;

public class DuplicateBudgetNameException extends ConflictException {

    public DuplicateBudgetNameException(String name) {
        super("A budget named '" + name + "' already exists");
    }
}
