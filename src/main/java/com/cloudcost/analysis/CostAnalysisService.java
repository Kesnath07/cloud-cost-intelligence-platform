package com.cloudcost.analysis;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.cloudcost.analysis.CostAggregationRepository.GroupTotal;
import com.cloudcost.common.DateRange;
import com.cloudcost.common.InvalidRequestException;
import com.cloudcost.common.MoneyMath;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calculates spending totals, trends and breakdowns for a period and compares them with the
 * immediately preceding period of equal length.
 */
@Service
@Transactional(readOnly = true)
public class CostAnalysisService {

    public static final int DEFAULT_PERIOD_DAYS = 30;
    static final int LARGEST_INCREASES_LIMIT = 5;

    private final CostAggregationRepository aggregations;
    private final Clock clock;

    public CostAnalysisService(CostAggregationRepository aggregations, Clock clock) {
        this.aggregations = aggregations;
        this.clock = clock;
    }

    /**
     * Resolves the period to analyze. Without explicit dates, the default is the 30 days ending
     * on the most recent day with stored data (Cost Explorer data lags by about a day, and
     * imported data may be historical), or ending today when nothing is stored.
     */
    public DateRange resolveRange(LocalDate start, LocalDate end) {
        if (start == null && end == null) {
            return DateRange.endingOn(latestDataDateOrToday(), DEFAULT_PERIOD_DAYS);
        }
        if (start == null || end == null) {
            throw new InvalidRequestException("Provide both a start date and an end date, or neither");
        }
        return DateRange.ofRequest(start, end);
    }

    public LocalDate latestDataDateOrToday() {
        LocalDate latest = aggregations.coverage().lastDate();
        return latest != null ? latest : LocalDate.now(clock);
    }

    public DataCoverage coverage() {
        return aggregations.coverage();
    }

    public List<String> availableServices() {
        return aggregations.distinctValues(CostDimension.SERVICE);
    }

    public List<String> availableEnvironments() {
        return aggregations.distinctValues(CostDimension.ENVIRONMENT);
    }

    public CostSummary summarize(DateRange range, CostFilter filter) {
        DateRange previous = range.previousPeriod();
        Set<LocalDate> dataDates = aggregations.datesWithData(range);
        Set<LocalDate> previousDataDates = aggregations.datesWithData(previous);

        List<DailyCost> daily = dailySeries(range, filter, dataDates);
        BigDecimal total = daily.stream().filter(DailyCost::hasData).map(DailyCost::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal previousTotal = aggregations.total(previous, filter);

        List<BreakdownItem> byService = breakdown(CostDimension.SERVICE, range, previous, filter, total);
        List<BreakdownItem> byEnvironment = breakdown(CostDimension.ENVIRONMENT, range, previous, filter, total);
        List<BreakdownItem> largestIncreases = byService.stream()
                .filter(item -> item.change().signum() > 0)
                .sorted(Comparator.comparing(BreakdownItem::change).reversed())
                .limit(LARGEST_INCREASES_LIMIT)
                .toList();

        return new CostSummary(range, previous, filter, total, previousTotal, total.subtract(previousTotal),
                MoneyMath.percentChange(total, previousTotal), MoneyMath.average(total, dataDates.size()),
                dataDates.size(), previousDataDates.size(), aggregations.containsEstimatedData(range), daily,
                monthly(range, daily), byService, byEnvironment, largestIncreases);
    }

    /** Monthly totals for the {@code months} calendar months ending with the month of {@code end}. */
    public List<MonthlyCost> monthlyTrend(LocalDate end, int months, CostFilter filter) {
        if (months < 1) {
            throw new InvalidRequestException("The number of months must be positive");
        }
        LocalDate start = YearMonth.from(end).minusMonths(months - 1L).atDay(1);
        DateRange range = DateRange.of(start, end);
        return monthly(range, dailySeries(range, filter, aggregations.datesWithData(range)));
    }

    /** Daily totals per dimension value, zero-filled on days that have data for other values. */
    public Map<String, TreeMap<LocalDate, BigDecimal>> dailySeriesBy(CostDimension dimension, DateRange range,
            CostFilter filter) {
        Set<LocalDate> dataDates = aggregations.datesWithData(range);
        Map<String, TreeMap<LocalDate, BigDecimal>> series = new HashMap<>();
        for (CostAggregationRepository.DailyGroupTotal row : aggregations.dailyTotalsBy(dimension, range, filter)) {
            series.computeIfAbsent(row.key(), key -> new TreeMap<>()).put(row.date(), row.amount());
        }
        for (TreeMap<LocalDate, BigDecimal> values : series.values()) {
            for (LocalDate date : dataDates) {
                values.putIfAbsent(date, BigDecimal.ZERO);
            }
        }
        return series;
    }

    /**
     * Daily costs as chart-ready series: one per value of {@code groupBy} ordered by total spend
     * (largest first), or a single total series when {@code groupBy} is {@code null}.
     */
    public DailyCostSeries dailyCostSeries(DateRange range, CostFilter filter, CostDimension groupBy) {
        Set<LocalDate> dataDates = aggregations.datesWithData(range);
        List<LocalDate> dates = range.dates().toList();
        List<DailyCostSeries.Series> series;
        if (groupBy == null) {
            series = List.of(toSeries(DailyCostSeries.TOTAL, aggregations.dailyTotals(range, filter), dates,
                    dataDates));
        } else {
            series = dailySeriesBy(groupBy, range, filter).entrySet().stream()
                    .map(entry -> toSeries(entry.getKey(), entry.getValue(), dates, dataDates))
                    .sorted(Comparator.comparing(DailyCostSeries.Series::total).reversed()
                            .thenComparing(DailyCostSeries.Series::name))
                    .toList();
        }
        return new DailyCostSeries(range, filter, groupBy, dates, series);
    }

    /** All days with ingested data in the range (independent of filters). */
    public Set<LocalDate> datesWithData(DateRange range) {
        return aggregations.datesWithData(range);
    }

    private List<DailyCost> dailySeries(DateRange range, CostFilter filter, Set<LocalDate> dataDates) {
        Map<LocalDate, BigDecimal> totals = aggregations.dailyTotals(range, filter);
        return range.dates()
                .map(date -> dataDates.contains(date)
                        ? new DailyCost(date, totals.getOrDefault(date, BigDecimal.ZERO), true)
                        : new DailyCost(date, null, false))
                .toList();
    }

    private static DailyCostSeries.Series toSeries(String name, Map<LocalDate, BigDecimal> totals,
            List<LocalDate> dates, Set<LocalDate> dataDates) {
        List<BigDecimal> amounts = dates.stream()
                .map(date -> dataDates.contains(date) ? totals.getOrDefault(date, BigDecimal.ZERO) : null)
                .toList();
        BigDecimal total = amounts.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new DailyCostSeries.Series(name, total, amounts);
    }

    private static List<MonthlyCost> monthly(DateRange range, List<DailyCost> daily) {
        Map<YearMonth, List<DailyCost>> byMonth = daily.stream()
                .collect(Collectors.groupingBy(day -> YearMonth.from(day.date()), TreeMap::new, Collectors.toList()));
        List<MonthlyCost> months = new ArrayList<>();
        byMonth.forEach((month, days) -> {
            List<DailyCost> withData = days.stream().filter(DailyCost::hasData).toList();
            BigDecimal amount = withData.stream().map(DailyCost::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            boolean partial = days.size() < month.lengthOfMonth();
            months.add(new MonthlyCost(month, amount, withData.size(), days.size(), partial));
        });
        return months;
    }

    private List<BreakdownItem> breakdown(CostDimension dimension, DateRange range, DateRange previous,
            CostFilter filter, BigDecimal total) {
        Map<String, BigDecimal> current = toMap(aggregations.totalsBy(dimension, range, filter));
        Map<String, BigDecimal> before = toMap(aggregations.totalsBy(dimension, previous, filter));
        Set<String> names = new LinkedHashSet<>(current.keySet());
        names.addAll(before.keySet());
        return names.stream()
                .map(name -> {
                    BigDecimal amount = current.getOrDefault(name, BigDecimal.ZERO);
                    BigDecimal previousAmount = before.getOrDefault(name, BigDecimal.ZERO);
                    return new BreakdownItem(name, amount, previousAmount, amount.subtract(previousAmount),
                            MoneyMath.percentChange(amount, previousAmount), MoneyMath.share(amount, total));
                })
                .sorted(Comparator.comparing(BreakdownItem::amount).reversed().thenComparing(BreakdownItem::name))
                .toList();
    }

    private static Map<String, BigDecimal> toMap(List<GroupTotal> totals) {
        return totals.stream().collect(Collectors.toMap(GroupTotal::key, GroupTotal::amount));
    }
}
