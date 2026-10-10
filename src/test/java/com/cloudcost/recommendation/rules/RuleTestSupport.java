package com.cloudcost.recommendation.rules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Function;

import com.cloudcost.budget.BudgetEvaluation;
import com.cloudcost.common.DateRange;
import com.cloudcost.costdata.ResourceUtilization;
import com.cloudcost.recommendation.RecommendationContext;
import com.cloudcost.recommendation.RecommendationProperties;

/** Builds recommendation contexts by hand so rules can be tested without a database. */
final class RuleTestSupport {

    /** 2026-09-01 (a Tuesday) to 2026-09-30. */
    static final DateRange CURRENT = DateRange.of(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
    static final DateRange PREVIOUS = CURRENT.previousPeriod();

    static final RecommendationProperties DEFAULTS = new RecommendationProperties(30, 14, new BigDecimal("20"),
            new BigDecimal("100.00"), List.of("development", "staging"), new BigDecimal("80"), new BigDecimal("5.00"),
            new BigDecimal("10"), new BigDecimal("50.00"), new BigDecimal("10"), new BigDecimal("40"),
            new BigDecimal("5"), 14, 90);

    private RuleTestSupport() {
    }

    static NavigableMap<LocalDate, BigDecimal> daily(DateRange range, Function<LocalDate, String> amount) {
        NavigableMap<LocalDate, BigDecimal> series = new TreeMap<>();
        range.dates().forEach(date -> series.put(date, new BigDecimal(amount.apply(date))));
        return series;
    }

    static BigDecimal sum(NavigableMap<LocalDate, BigDecimal> series) {
        return series.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static RecommendationContext context(Map<String, BigDecimal> currentByService,
            Map<String, BigDecimal> previousByService, Map<String, NavigableMap<LocalDate, BigDecimal>> dailyByService,
            Map<String, NavigableMap<LocalDate, BigDecimal>> dailyByEnvironment,
            Map<String, BigDecimal> untaggedByService, List<BudgetEvaluation> budgets,
            List<ResourceUtilization> utilization) {
        BigDecimal total = currentByService.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return new RecommendationContext(CURRENT, PREVIOUS, 30, 30, total, currentByService, previousByService,
                dailyByService, dailyByEnvironment, untaggedByService, budgets, utilization, "USD");
    }
}
