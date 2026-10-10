package com.cloudcost.anomaly;

public enum AnomalyStatus {
    /** Detected and not yet reviewed. */
    OPEN,
    /** Reviewed and confirmed as worth following up. */
    ACKNOWLEDGED,
    /** Reviewed and judged expected or irrelevant. */
    DISMISSED
}
