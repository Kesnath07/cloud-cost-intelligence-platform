package com.cloudcost.ingestion;

import java.util.List;

/**
 * Outcome of parsing an import file. A file is imported only when it has no errors at
 * all, so a partially valid file never produces a partial import.
 */
public record ParseResult<T>(List<T> records, List<RowError> errors, int dataRows, boolean errorsTruncated) {

    public ParseResult {
        records = List.copyOf(records);
        errors = List.copyOf(errors);
    }

    /** Builds a result, keeping at most {@code maxErrors} errors (and no records if any error occurred). */
    static <T> ParseResult<T> of(List<T> records, List<RowError> errors, int dataRows, int maxErrors) {
        if (errors.isEmpty()) {
            return new ParseResult<>(records, List.of(), dataRows, false);
        }
        boolean truncated = errors.size() > maxErrors;
        return new ParseResult<>(List.of(), truncated ? errors.subList(0, maxErrors) : errors, dataRows, truncated);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
