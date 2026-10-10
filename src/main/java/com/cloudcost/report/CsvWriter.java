package com.cloudcost.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal RFC 4180 CSV writer. Fields containing commas, quotes or line breaks are quoted and
 * embedded quotes doubled. Text fields that a spreadsheet would interpret as a formula
 * (starting with =, +, -, @, tab or carriage return) are prefixed with an apostrophe to
 * prevent CSV injection; numeric fields are written as plain decimals and never prefixed,
 * so negative amounts stay numeric.
 */
public final class CsvWriter {

    private static final String LINE_END = "\r\n";

    private final StringBuilder out = new StringBuilder();
    private List<String> row = new ArrayList<>();

    public CsvWriter text(String value) {
        row.add(escape(neutralizeFormula(value == null ? "" : value)));
        return this;
    }

    /** A monetary amount with two decimal places, or an empty field. */
    public CsvWriter amount(BigDecimal value) {
        row.add(value == null ? "" : value.setScale(2, RoundingMode.HALF_UP).toPlainString());
        return this;
    }

    public CsvWriter number(BigDecimal value) {
        row.add(value == null ? "" : value.toPlainString());
        return this;
    }

    public CsvWriter number(long value) {
        row.add(Long.toString(value));
        return this;
    }

    public CsvWriter endRow() {
        out.append(String.join(",", row)).append(LINE_END);
        row = new ArrayList<>();
        return this;
    }

    public CsvWriter header(String... names) {
        for (String name : names) {
            text(name);
        }
        return endRow();
    }

    @Override
    public String toString() {
        return out.toString();
    }

    static String neutralizeFormula(String value) {
        if (!value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0) {
            return "'" + value;
        }
        return value;
    }

    static String escape(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
