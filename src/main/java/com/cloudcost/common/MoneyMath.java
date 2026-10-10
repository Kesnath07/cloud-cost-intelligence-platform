package com.cloudcost.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Financial arithmetic on {@link BigDecimal}. Amounts keep their stored precision (six decimal
 * places) through aggregation; only percentages are rounded, to two decimal places, half-up.
 * Methods return {@code null} where a percentage is mathematically undefined, rather than
 * inventing a value such as 0% or 100%.
 */
public final class MoneyMath {

    public static final int PERCENT_SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private MoneyMath() {
    }

    public static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * Percentage change from {@code previous} to {@code current}, relative to the magnitude of
     * the previous value, so a move from -10 (net credit) to -5 is reported as +50%.
     * Returns {@code null} when there is no non-zero baseline.
     */
    public static BigDecimal percentChange(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null || previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous).multiply(HUNDRED).divide(previous.abs(), PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Share of {@code part} in {@code total} as a percentage. Undefined ({@code null}) when the
     * total is zero or negative, which happens when credits exceed charges.
     */
    public static BigDecimal share(BigDecimal part, BigDecimal total) {
        if (part == null || total == null || total.signum() <= 0) {
            return null;
        }
        return part.multiply(HUNDRED).divide(total, PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    /** {@code numerator / denominator} as a percentage; {@code null} when the denominator is zero. */
    public static BigDecimal percentOf(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.signum() == 0) {
            return null;
        }
        return numerator.multiply(HUNDRED).divide(denominator, PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    /** Average per unit (for example per day), keeping six decimal places. */
    public static BigDecimal average(BigDecimal total, long count) {
        if (total == null || count <= 0) {
            return null;
        }
        return total.divide(BigDecimal.valueOf(count), 6, RoundingMode.HALF_UP);
    }
}
