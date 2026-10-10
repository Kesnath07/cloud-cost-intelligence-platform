package com.cloudcost.costdata;

public enum ImportStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    /** The job completed but its records were later removed by a user. */
    REMOVED
}
