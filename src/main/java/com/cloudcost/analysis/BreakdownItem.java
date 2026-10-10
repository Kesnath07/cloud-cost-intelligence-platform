package com.cloudcost.analysis;

import java.math.BigDecimal;

/**
 * Spending of one service or environment in the current period compared with the previous
 * period. Percentages are {@code null} when undefined (no previous spend, or a non-positive total).
 */
public record BreakdownItem(
        String name,
        BigDecimal amount,
        BigDecimal previousAmount,
        BigDecimal change,
        BigDecimal changePercent,
        BigDecimal sharePercent) {
}
