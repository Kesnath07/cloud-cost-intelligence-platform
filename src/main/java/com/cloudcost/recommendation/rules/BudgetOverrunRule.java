package com.cloudcost.recommendation.rules;

import java.util.ArrayList;
import java.util.List;

import com.cloudcost.budget.BudgetEvaluation;
import com.cloudcost.budget.BudgetStatus;
import com.cloudcost.common.Severity;
import com.cloudcost.recommendation.EvidenceSource;
import com.cloudcost.recommendation.RecommendationCandidate;
import com.cloudcost.recommendation.RecommendationCategory;
import com.cloudcost.recommendation.RecommendationContext;
import com.cloudcost.recommendation.RecommendationRule;

import org.springframework.stereotype.Component;

/** Active budgets that are exceeded, or whose linear run-rate projection reaches the limit. */
@Component
public class BudgetOverrunRule implements RecommendationRule {

    static final String ID = "BUDGET_OVERRUN";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<RecommendationCandidate> evaluate(RecommendationContext context) {
        List<RecommendationCandidate> candidates = new ArrayList<>();
        for (BudgetEvaluation budget : context.budgets()) {
            boolean exceeded = budget.status() == BudgetStatus.EXCEEDED;
            if (!exceeded && !budget.projectedToExceed()) {
                continue;
            }
            List<String> evidence = new ArrayList<>();
            evidence.add("Spent " + context.money(budget.spend()) + " of " + context.money(budget.limit()) + " ("
                    + budget.utilizationPercent().toPlainString() + "%) from " + budget.period().start() + " to "
                    + budget.asOf() + ".");
            if (budget.projectedSpend() != null) {
                evidence.add("Linear run-rate projection to " + budget.period().end() + ": "
                        + context.money(budget.projectedSpend()) + " ("
                        + budget.projectedUtilizationPercent().toPlainString() + "% of the limit).");
            }
            evidence.add("Budget scope: " + budget.scope() + ".");
            String kind = exceeded ? "EXCEEDED" : "PROJECTED";
            candidates.add(new RecommendationCandidate(
                    ID + "|" + budget.budgetId() + "|" + budget.period().start() + "|" + kind, ID,
                    (exceeded ? "Budget exceeded: " : "Budget projected to be exceeded: ") + budget.name(),
                    RecommendationCategory.BUDGET, exceeded ? Severity.HIGH : Severity.MEDIUM, null, null, null,
                    EvidenceSource.BUDGET_EVALUATION, evidence,
                    exceeded ? "Spending in this period has reached the configured limit."
                            : "At the average daily rate so far, spending would reach the limit before the period "
                                    + "ends. A linear projection ignores planned changes and charges that are billed "
                                    + "once per month.",
                    "Review the largest services within the budget's scope on the Cost Explorer page and any open "
                            + "anomalies in the period. If the higher spending is expected, adjust the budget.",
                    null, "Not applicable: a budget overrun identifies where to look, not a specific saving.",
                    budget.period()));
        }
        return candidates;
    }
}
