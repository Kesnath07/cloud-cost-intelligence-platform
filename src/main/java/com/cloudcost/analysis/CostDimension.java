package com.cloudcost.analysis;

import com.cloudcost.common.InvalidRequestException;

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

    /** Parses a request parameter such as {@code service} or {@code ENVIRONMENT}, ignoring case. */
    public static CostDimension fromParameter(String value) {
        for (CostDimension dimension : values()) {
            if (dimension.name().equalsIgnoreCase(value == null ? "" : value.strip())) {
                return dimension;
            }
        }
        throw new InvalidRequestException("Unknown dimension '" + value + "'. Use one of: service, environment");
    }
}
