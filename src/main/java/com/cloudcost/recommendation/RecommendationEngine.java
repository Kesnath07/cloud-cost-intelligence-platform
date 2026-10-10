package com.cloudcost.recommendation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.stream.Collectors;

import com.cloudcost.analysis.CostAggregationRepository;
import com.cloudcost.analysis.CostAnalysisService;
import com.cloudcost.analysis.CostDimension;
import com.cloudcost.analysis.CostFilter;
import com.cloudcost.budget.BudgetService;
import com.cloudcost.common.DateRange;
import com.cloudcost.config.PlatformProperties;
import com.cloudcost.costdata.ResourceUtilization;
import com.cloudcost.costdata.ResourceUtilizationRepository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Assembles a {@link RecommendationContext} from stored data and runs every rule against it. */
@Component
public class RecommendationEngine {

    private final List<RecommendationRule> rules;
    private final CostAnalysisService analysis;
    private final CostAggregationRepository aggregations;
    private final BudgetService budgets;
    private final ResourceUtilizationRepository utilization;
    private final RecommendationProperties properties;
    private final PlatformProperties platformProperties;

    public RecommendationEngine(List<RecommendationRule> rules, CostAnalysisService analysis,
            CostAggregationRepository aggregations, BudgetService budgets, ResourceUtilizationRepository utilization,
            RecommendationProperties properties, PlatformProperties platformProperties) {
        this.rules = rules;
        this.analysis = analysis;
        this.aggregations = aggregations;
        this.budgets = budgets;
        this.utilization = utilization;
        this.properties = properties;
        this.platformProperties = platformProperties;
    }

    @Transactional(readOnly = true)
    public List<RecommendationCandidate> run() {
        RecommendationContext context = buildContext();
        List<RecommendationCandidate> candidates = new ArrayList<>();
        for (RecommendationRule rule : rules) {
            candidates.addAll(rule.evaluate(context));
        }
        return candidates;
    }

    RecommendationContext buildContext() {
        DateRange current = DateRange.endingOn(budgets.defaultAsOf(), properties.windowDays());
        DateRange previous = current.previousPeriod();
        Map<String, BigDecimal> currentByService = totals(CostDimension.SERVICE, current, CostFilter.NONE);
        BigDecimal currentTotal = currentByService.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return new RecommendationContext(current, previous,
                aggregations.datesWithData(current).size(), aggregations.datesWithData(previous).size(), currentTotal,
                currentByService, totals(CostDimension.SERVICE, previous, CostFilter.NONE),
                navigable(analysis.dailySeriesBy(CostDimension.SERVICE, current, CostFilter.NONE)),
                navigable(analysis.dailySeriesBy(CostDimension.ENVIRONMENT, current, CostFilter.NONE)),
                totals(CostDimension.SERVICE, current, CostFilter.environment("")),
                budgets.evaluateActive(current.end()), latestMeasurementPerResource(),
                platformProperties.reportingCurrency());
    }

    private Map<String, BigDecimal> totals(CostDimension dimension, DateRange range, CostFilter filter) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        aggregations.totalsBy(dimension, range, filter).stream()
                .sorted(Comparator.comparing(CostAggregationRepository.GroupTotal::amount).reversed())
                .forEach(total -> totals.put(total.key(), total.amount()));
        return totals;
    }

    private static Map<String, NavigableMap<LocalDate, BigDecimal>> navigable(
            Map<String, ? extends NavigableMap<LocalDate, BigDecimal>> series) {
        return new HashMap<>(series);
    }

    private List<ResourceUtilization> latestMeasurementPerResource() {
        return utilization.findAll().stream()
                .collect(Collectors.toMap(ResourceUtilization::getResourceId, measurement -> measurement,
                        (a, b) -> a.getPeriodEnd().isAfter(b.getPeriodEnd()) ? a : b))
                .values().stream()
                .sorted(Comparator.comparing(ResourceUtilization::getResourceId))
                .toList();
    }
}
