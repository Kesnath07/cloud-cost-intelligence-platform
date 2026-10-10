package com.cloudcost.costdata;

/** Where a cost record or import job came from. */
public enum CostSource {
    /** A CSV file uploaded by a user. */
    CSV_UPLOAD("CSV upload"),
    /** The bundled, fictional demonstration dataset. Never real AWS spending. */
    DEMO_SAMPLE("Demonstration data"),
    /** Retrieved from the AWS Cost Explorer GetCostAndUsage API. */
    AWS_COST_EXPLORER("AWS Cost Explorer");

    private final String label;

    CostSource(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
