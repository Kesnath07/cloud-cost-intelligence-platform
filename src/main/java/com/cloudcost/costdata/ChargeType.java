package com.cloudcost.costdata;

/**
 * Type of charge represented by a cost record. Credits and refunds are negative amounts;
 * NET is used for Cost Explorer data, which reports a single amount net of all record types.
 */
public enum ChargeType {
    USAGE,
    TAX,
    CREDIT,
    REFUND,
    NET;

    /** Whether an amount of the given sign is plausible for this charge type. */
    public boolean permitsSign(int signum) {
        return switch (this) {
            case USAGE, TAX -> signum >= 0;
            case CREDIT, REFUND -> signum <= 0;
            case NET -> true;
        };
    }
}
