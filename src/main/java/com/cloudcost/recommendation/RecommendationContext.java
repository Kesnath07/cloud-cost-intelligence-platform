package com.cloudcost.recommendation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

import com.cloudcost.budget.BudgetEvaluation;
import com.cloudcost.common.DateRange;
import com.cloudcost.costdata.ResourceUtilization;

/**
 * Everything the rules may look at, assembled once per generation run so that rules are pure
 * functions of this snapshot and can be unit tested without a database.
 *
 * @param currentWindow     analysis window ending on the latest day with cost data
 * @param previousWindow    window of equal length immediately before it
 * @param currentByService  spend per service in the current window
 * @param previousByService spend per service in the previous window
 * @param dailyByService    daily spend per service in the current window (zero-filled on data days)
 * @param dailyByEnvironment daily spend per environment in the current window ('' = untagged)
 * @param untaggedByService spend without an environment tag per service in the current window
 * @param budgets           evaluations of active budgets as of the window end
 * @param utilization       latest utilization measurement per resource
 */
public record RecommendationContext(
        DateRange currentWindow,
        DateRange previousWindow,
        int currentDaysWithData,
        int previousDaysWithData,
        BigDecimal currentTotal,
        Map<String, BigDecimal> currentByService,
        Map<String, BigDecimal> previousByService,
        Map<String, NavigableMap<LocalDate, BigDecimal>> dailyByService,
        Map<String, NavigableMap<LocalDate, BigDecimal>> dailyByEnvironment,
        Map<String, BigDecimal> untaggedByService,
        List<BudgetEvaluation> budgets,
        List<ResourceUtilization> utilization,
        String currency) {

    public LocalDate asOf() {
        return currentWindow.end();
    }

    /** Month used in fingerprints, so an unresolved issue yields a new recommendation each month. */
    public YearMonth month() {
        return YearMonth.from(asOf());
    }

    public String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString() + " " + currency;
    }
}
