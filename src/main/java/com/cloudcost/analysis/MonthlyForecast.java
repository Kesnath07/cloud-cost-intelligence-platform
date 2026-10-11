package com.cloudcost.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Month-to-date spend and two simple projections to the end of the calendar month. Projections
 * are {@code null} until {@link CostForecastService#MIN_DAYS_FOR_PROJECTION} days have elapsed
 * and once the month is complete (then {@code monthToDate} is the actual monthly total).
 *
 * @param asOf                  last day included in {@code monthToDate}: the latest day with
 *                              stored data, capped at the month end; {@code null} when no data is
 *                              stored for the month yet
 * @param linearProjection      month-to-date spend / elapsed days &times; days in month (the same
 *                              rule as budget projections)
 * @param recentDailyAverage    average daily spend over the {@link CostForecastService#RECENT_DAYS}
 *                              days ending on {@code asOf}, counting days with data only
 * @param recentRateProjection  month-to-date spend + recent daily average &times; remaining days,
 *                              which reacts faster to recent changes than the linear projection
 * @param projectedChangePercent change of the linear projection over the previous month's total
 */
public record MonthlyForecast(
        YearMonth month,
        CostFilter filter,
        LocalDate asOf,
        long daysElapsed,
        int daysInMonth,
        int daysWithData,
        boolean complete,
        BigDecimal monthToDate,
        BigDecimal linearProjection,
        BigDecimal recentDailyAverage,
        BigDecimal recentRateProjection,
        BigDecimal previousMonthTotal,
        BigDecimal projectedChangePercent) {
}
