package com.cloudcost.analysis;

/**
 * Optional restriction of an analysis to one service and/or one environment. A {@code null}
 * field means "all". The environment value {@code ""} selects untagged costs.
 */
public record CostFilter(String service, String environment) {

    /** Display and request-parameter label for costs without an environment tag. */
    public static final String UNTAGGED_LABEL = "(untagged)";

    public static final CostFilter NONE = new CostFilter(null, null);

    /** Builds a filter from request parameters, where blank means "all" and "(untagged)" means untagged. */
    public static CostFilter fromRequest(String service, String environment) {
        return new CostFilter(blankToNull(service), environmentParameter(environment));
    }

    public static CostFilter service(String service) {
        return new CostFilter(service, null);
    }

    public static CostFilter environment(String environment) {
        return new CostFilter(null, environment);
    }

    public boolean isEmpty() {
        return service == null && environment == null;
    }

    /** Request-parameter form of the environment, the inverse of {@link #fromRequest}. */
    public String environmentParameter() {
        if (environment == null) {
            return null;
        }
        return environment.isEmpty() ? UNTAGGED_LABEL : environment;
    }

    private static String environmentParameter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UNTAGGED_LABEL.equals(value.strip()) ? "" : value.strip();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
