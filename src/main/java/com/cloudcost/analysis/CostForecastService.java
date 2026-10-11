package com.cloudcost.analysis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;

import com.cloudcost.common.DateRange;
import com.cloudcost.common.MoneyMath;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Projects calendar-month spend from month-to-date data. Both projections are deliberately
 * simple run-rate extrapolations: they ignore planned changes, seasonality and charges billed
 * once per month, and are labeled as projections wherever they are shown.
 */
@Service
@Transactional(readOnly = true)
public class CostForecastService {

    /** Same minimum as budget projections, so both never disagree about when a projection is shown. */
    public static final int MIN_DAYS_FOR_PROJECTION = 7;
    public static final int RECENT_DAYS = 7;

    private final CostAggregationRepository aggregations;
    private final CostAnalysisService analysis;
    private final Clock clock;

    public CostForecastService(CostAggregationRepository aggregations, CostAnalysisService analysis, Clock clock) {
        this.aggregations = aggregations;
        this.analysis = analysis;
        this.clock = clock;
    }

    /** The month containing the latest day with stored data, or the current month when nothing is stored. */
    public YearMonth defaultMonth() {
        LocalDate today = LocalDate.now(clock);
        LocalDate latest = analysis.latestDataDateOrToday();
        return YearMonth.from(latest.isAfter(today) ? today : latest);
    }

    public MonthlyForecast forecast(YearMonth month, CostFilter filter) {
        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();
        BigDecimal previousMonthTotal = aggregations.total(DateRange.of(first.minusMonths(1),
                first.minusDays(1)), filter);
        LocalDate latest = aggregations.coverage().lastDate();

        if (latest == null || latest.isBefore(first)) {
            return new MonthlyForecast(month, filter, null, 0, month.lengthOfMonth(), 0, false, BigDecimal.ZERO,
                    null, null, null, previousMonthTotal, null);
        }

        LocalDate asOf = latest.isAfter(last) ? last : latest;
        DateRange elapsed = DateRange.of(first, asOf);
        BigDecimal monthToDate = aggregations.total(elapsed, filter);
        int daysWithData = aggregations.datesWithData(elapsed).size();
        long daysElapsed = elapsed.days();
        int daysInMonth = month.lengthOfMonth();
        boolean complete = asOf.equals(last);

        DateRange recent = DateRange.endingOn(asOf, RECENT_DAYS);
        BigDecimal recentDailyAverage = MoneyMath.average(aggregations.total(recent, filter),
                aggregations.datesWithData(recent).size());

        BigDecimal linear = null;
        BigDecimal recentRate = null;
        if (!complete && daysElapsed >= MIN_DAYS_FOR_PROJECTION) {
            linear = monthToDate.multiply(BigDecimal.valueOf(daysInMonth))
                    .divide(BigDecimal.valueOf(daysElapsed), 2, RoundingMode.HALF_UP);
            if (recentDailyAverage != null) {
                recentRate = monthToDate.add(recentDailyAverage.multiply(BigDecimal.valueOf(daysInMonth - daysElapsed)))
                        .setScale(2, RoundingMode.HALF_UP);
            }
        }
        BigDecimal comparable = complete ? monthToDate : linear;
        return new MonthlyForecast(month, filter, asOf, daysElapsed, daysInMonth, daysWithData, complete, monthToDate,
                linear, recentDailyAverage, recentRate, previousMonthTotal,
                MoneyMath.percentChange(comparable, previousMonthTotal));
    }
}
