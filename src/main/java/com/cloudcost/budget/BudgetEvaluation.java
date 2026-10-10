package com.cloudcost.budget;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.cloudcost.common.DateRange;

/**
 * A budget evaluated for the calendar period containing {@code asOf}.
 *
 * @param spend              actual spend from the period start through {@code asOf}
 * @param utilizationPercent spend as a percentage of the limit
 * @param projectedSpend     linear run-rate projection to the end of the period; {@code null}
 *                           when fewer than {@link BudgetService#MIN_DAYS_FOR_PROJECTION} days
 *                           have elapsed or the period is complete
 */
public record BudgetEvaluation(
        long budgetId,
        String name,
        BudgetPeriod periodType,
        DateRange period,
        LocalDate asOf,
        BigDecimal limit,
        BigDecimal spend,
        BigDecimal remaining,
        BigDecimal utilizationPercent,
        int warningThresholdPercent,
        BudgetStatus status,
        long daysElapsed,
        long daysInPeriod,
        int daysWithData,
        BigDecimal projectedSpend,
        BigDecimal projectedUtilizationPercent,
        String scope) {

    public boolean projectedToExceed() {
        return projectedSpend != null && projectedSpend.compareTo(limit) >= 0;
    }

    public boolean periodComplete() {
        return !asOf.isBefore(period.end());
    }
}
