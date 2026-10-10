package com.cloudcost.ingestion;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Normalizes environment tag values: trims, lower-cases and maps common abbreviations
 * (prod, dev, stg, ...) to one spelling. An empty result means the cost was untagged.
 */
public final class EnvironmentNormalizer {

    public static final int MAX_LENGTH = 64;
    private static final Pattern VALID = Pattern.compile("[a-z0-9][a-z0-9._-]*");
    private static final Map<String, String> ALIASES = Map.of(
            "prod", "production",
            "prd", "production",
            "dev", "development",
            "stage", "staging",
            "stg", "staging",
            "tst", "test");

    private EnvironmentNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String value = raw.strip().toLowerCase(Locale.ROOT);
        return ALIASES.getOrDefault(value, value);
    }

    /** Whether a normalized value is acceptable for storage. */
    public static boolean isValid(String normalized) {
        return normalized.isEmpty() || (normalized.length() <= MAX_LENGTH && VALID.matcher(normalized).matches());
    }
}
