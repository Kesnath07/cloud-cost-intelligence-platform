package com.cloudcost.analysis;

import java.math.BigDecimal;
import java.util.List;

import com.cloudcost.common.DateRange;

/** Complete analysis of one period, compared with the immediately preceding period of equal length. */
public record CostSummary(
        DateRange range,
        DateRange previousRange,
        CostFilter filter,
        BigDecimal total,
        BigDecimal previousTotal,
        BigDecimal change,
        BigDecimal changePercent,
        BigDecimal averageDailyCost,
        int daysWithData,
        int previousDaysWithData,
        boolean containsEstimatedData,
        List<DailyCost> daily,
        List<MonthlyCost> monthly,
        List<BreakdownItem> byService,
        List<BreakdownItem> byEnvironment,
        List<BreakdownItem> largestIncreases) {

    public boolean hasData() {
        return daysWithData > 0;
    }

    public boolean hasPreviousData() {
        return previousDaysWithData > 0;
    }

    /** Whether some days in the period have no ingested data, which understates the total. */
    public boolean hasGaps() {
        return daysWithData > 0 && daysWithData < range.days();
    }
}
