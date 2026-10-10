package com.cloudcost.analysis;

/** Dimensions that cost data can be broken down by. */
public enum CostDimension {
    SERVICE("serviceName", "Service"),
    ENVIRONMENT("environment", "Environment");

    private final String attribute;
    private final String label;

    CostDimension(String attribute, String label) {
        this.attribute = attribute;
        this.label = label;
    }

    /** JPA attribute of {@code CostRecord} holding this dimension. */
    String attribute() {
        return attribute;
    }

    public String label() {
        return label;
    }
}
