package com.cloudcost.ingestion;

/**
 * A validation problem in an imported file. {@code row} uses spreadsheet numbering: the
 * header is row 1 and the first data row is row 2. A row of 0 refers to the whole file.
 */
public record RowError(long row, String column, String message) {

    public static RowError file(String message) {
        return new RowError(0, null, message);
    }

    public String describe() {
        StringBuilder text = new StringBuilder();
        if (row > 0) {
            text.append("Row ").append(row);
            if (column != null) {
                text.append(", column '").append(column).append('\'');
            }
            text.append(": ");
        }
        return text.append(message).toString();
    }
}
