package com.cloudcost.ingestion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validating accessors for individual CSV values. Each method returns {@code null} and
 * records a {@link RowError} when the value is invalid, so that all problems in a row are
 * reported together instead of failing on the first one.
 */
final class FieldReader {

    /** Monetary amounts are stored with six decimal places (Cost Explorer reports up to ten). */
    static final int AMOUNT_SCALE = 6;
    static final BigDecimal MAX_ABSOLUTE_AMOUNT = new BigDecimal("1000000000000");
    static final LocalDate EARLIEST_DATE = LocalDate.of(2000, 1, 1);

    private static final Pattern DECIMAL = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final Pattern REGION = Pattern.compile("[a-z]{2}(-[a-z]+)+-\\d{1,2}|global");
    private static final Pattern ACCOUNT_ID = Pattern.compile("\\d{12}");

    private final CsvTable.Row row;
    private final List<RowError> errors;
    private final LocalDate today;

    FieldReader(CsvTable.Row row, List<RowError> errors, LocalDate today) {
        this.row = row;
        this.errors = errors;
        this.today = today;
    }

    void error(String column, String message) {
        errors.add(new RowError(row.number(), column, message));
    }

    long rowNumber() {
        return row.number();
    }

    String optionalText(String column) {
        return row.get(column);
    }

    String requiredText(String column, int maxLength) {
        String value = row.get(column);
        if (value.isEmpty()) {
            error(column, "A value is required");
            return null;
        }
        if (value.length() > maxLength) {
            error(column, "Must be at most " + maxLength + " characters");
            return null;
        }
        return value;
    }

    LocalDate date(String column) {
        String value = row.get(column);
        if (value.isEmpty()) {
            error(column, "A date is required (YYYY-MM-DD)");
            return null;
        }
        LocalDate date;
        try {
            date = LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            error(column, "'" + value + "' is not a valid date; expected YYYY-MM-DD");
            return null;
        }
        if (date.isAfter(today)) {
            error(column, "Date " + date + " is in the future (today is " + today + " UTC)");
            return null;
        }
        if (date.isBefore(EARLIEST_DATE)) {
            error(column, "Date " + date + " is before " + EARLIEST_DATE);
            return null;
        }
        return date;
    }

    BigDecimal amount(String column, boolean required) {
        String value = row.get(column);
        if (value.isEmpty()) {
            if (required) {
                error(column, "An amount is required");
            }
            return null;
        }
        if (!DECIMAL.matcher(value).matches()) {
            error(column, "'" + value + "' is not a valid amount; use digits with an optional '.' decimal "
                    + "separator, without currency symbols or thousands separators");
            return null;
        }
        BigDecimal amount = new BigDecimal(value).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        if (amount.abs().compareTo(MAX_ABSOLUTE_AMOUNT) >= 0) {
            error(column, "Amount " + value + " is outside the supported range");
            return null;
        }
        return amount;
    }

    BigDecimal percentage(String column) {
        String value = row.get(column);
        if (value.isEmpty()) {
            error(column, "A percentage between 0 and 100 is required");
            return null;
        }
        if (!DECIMAL.matcher(value).matches()) {
            error(column, "'" + value + "' is not a valid percentage");
            return null;
        }
        BigDecimal percentage = new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
        if (percentage.signum() < 0 || percentage.compareTo(BigDecimal.valueOf(100)) > 0) {
            error(column, "Percentage " + value + " must be between 0 and 100");
            return null;
        }
        return percentage;
    }

    /** Validates the currency against the single supported reporting currency. */
    String currency(String column, String reportingCurrency, boolean required) {
        String value = row.get(column).toUpperCase(Locale.ROOT);
        if (value.isEmpty()) {
            if (required) {
                error(column, "A currency code is required (" + reportingCurrency + ")");
            }
            return null;
        }
        if (!value.equals(reportingCurrency)) {
            error(column, "Currency '" + value + "' does not match the reporting currency " + reportingCurrency
                    + "; currency conversion is not supported");
            return null;
        }
        return value;
    }

    String region(String column) {
        String value = row.get(column).toLowerCase(Locale.ROOT);
        if (!value.isEmpty() && !REGION.matcher(value).matches()) {
            error(column, "'" + value + "' is not an AWS region code (for example us-east-1) or 'global'");
            return null;
        }
        return value;
    }

    String accountId(String column) {
        String value = row.get(column);
        if (!value.isEmpty() && !ACCOUNT_ID.matcher(value).matches()) {
            error(column, "AWS account IDs must be exactly 12 digits");
            return null;
        }
        return value;
    }

    String serviceName(String column) {
        String value = ServiceNameNormalizer.normalize(row.get(column));
        if (value.isEmpty()) {
            error(column, "A service name is required");
            return null;
        }
        if (value.length() > ServiceNameNormalizer.MAX_LENGTH) {
            error(column, "Service names must be at most " + ServiceNameNormalizer.MAX_LENGTH + " characters");
            return null;
        }
        return value;
    }

    String environment(String column) {
        String value = EnvironmentNormalizer.normalize(row.get(column));
        if (!EnvironmentNormalizer.isValid(value)) {
            error(column, "Environment values may contain only letters, digits, '.', '_' and '-' (at most "
                    + EnvironmentNormalizer.MAX_LENGTH + " characters)");
            return null;
        }
        return value;
    }
}
