package com.cloudcost.report;

import java.time.Instant;
import java.util.List;

import com.cloudcost.analysis.CostSummary;
import com.cloudcost.anomaly.CostAnomaly;
import com.cloudcost.budget.BudgetEvaluation;
import com.cloudcost.recommendation.OptimizationRecommendation;

/**
 * Everything included in a cost summary report for one period.
 *
 * @param budgets         active budgets evaluated as of the end of the reporting period
 * @param anomalies       anomaly findings dated within the period (any review status)
 * @param recommendations open and accepted recommendations at generation time
 */
public record CostReport(
        Instant generatedAt,
        String currency,
        boolean includesDemonstrationData,
        CostSummary summary,
        List<BudgetEvaluation> budgets,
        List<CostAnomaly> anomalies,
        List<OptimizationRecommendation> recommendations) {
}
