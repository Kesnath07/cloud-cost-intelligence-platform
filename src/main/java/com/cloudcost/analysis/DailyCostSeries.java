package com.cloudcost.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.cloudcost.common.DateRange;

/**
 * Daily costs of a period as parallel arrays, ready for charting: {@code dates} lists every day
 * of the range and each series has one amount per date. An amount is {@code null} on days with
 * no ingested data at all (a gap) and zero on days with data but no spend for that series.
 *
 * @param groupBy {@code null} when the period is not split, in which case there is a single
 *                series named {@link #TOTAL}
 */
public record DailyCostSeries(DateRange range, CostFilter filter, CostDimension groupBy, List<LocalDate> dates,
        List<Series> series) {

    public static final String TOTAL = "Total";

    /** One line of the chart. Environment series use {@code ""} for untagged costs. */
    public record Series(String name, BigDecimal total, List<BigDecimal> amounts) {
    }
}
