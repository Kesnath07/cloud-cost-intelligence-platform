package com.cloudcost.budget;

public enum BudgetStatus {
    /** Spending is below the warning threshold. */
    NORMAL,
    /** Spending reached the warning threshold but is below the limit. */
    WARNING,
    /** Spending reached or passed the limit. */
    EXCEEDED
}
