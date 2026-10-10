package com.cloudcost.recommendation;

/** The kind of data a recommendation's evidence comes from. */
public enum EvidenceSource {
    /** Aggregated cost records; shows how much was spent, not how resources are used. */
    BILLING_DATA("Billing data"),
    /** Configured budgets compared with billing data. */
    BUDGET_EVALUATION("Budget evaluation"),
    /** Measured resource utilization supplied by the user. */
    UTILIZATION_DATA("Utilization measurements");

    private final String label;

    EvidenceSource(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
