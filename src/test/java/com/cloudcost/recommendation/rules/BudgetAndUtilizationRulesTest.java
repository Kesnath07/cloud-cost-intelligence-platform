package com.cloudcost.recommendation.rules;

import static com.cloudcost.recommendation.rules.RuleTestSupport.CURRENT;
import static com.cloudcost.recommendation.rules.RuleTestSupport.DEFAULTS;
import static com.cloudcost.recommendation.rules.RuleTestSupport.context;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.cloudcost.budget.BudgetEvaluation;
import com.cloudcost.budget.BudgetPeriod;
import com.cloudcost.budget.BudgetStatus;
import com.cloudcost.common.Severity;
import com.cloudcost.costdata.ResourceUtilization;
import com.cloudcost.recommendation.EvidenceSource;
import com.cloudcost.recommendation.RecommendationCandidate;
import com.cloudcost.recommendation.RecommendationCategory;

import org.junit.jupiter.api.Test;

class BudgetAndUtilizationRulesTest {

    private static BudgetEvaluation budget(String spend, BudgetStatus status, String projected) {
        BigDecimal limit = new BigDecimal("500.00");
        BigDecimal spent = new BigDecimal(spend);
        BigDecimal projection = projected == null ? null : new BigDecimal(projected);
        return new BudgetEvaluation(7, "Production", BudgetPeriod.MONTHLY, CURRENT, LocalDate.of(2026, 9, 20), limit,
                spent, limit.subtract(spent), spent.divide(new BigDecimal("5")), 80, status, 20, 30, 20, projection,
                projection == null ? null : projection.divide(new BigDecimal("5")), "All services, production");
    }

    private static ResourceUtilization measurement(String id, String avg, String max, String cost, int days) {
        ResourceUtilization m = mock(ResourceUtilization.class);
        when(m.getResourceId()).thenReturn(id);
        when(m.getResourceType()).thenReturn("m6i.xlarge");
        when(m.getServiceName()).thenReturn("Amazon Elastic Compute Cloud - Compute");
        when(m.getEnvironment()).thenReturn("development");
        when(m.getPeriodStart()).thenReturn(LocalDate.of(2026, 9, 30).minusDays(days - 1L));
        when(m.getPeriodEnd()).thenReturn(LocalDate.of(2026, 9, 30));
        when(m.periodDays()).thenReturn((long) days);
        when(m.getAvgCpuPercent()).thenReturn(new BigDecimal(avg));
        when(m.getMaxCpuPercent()).thenReturn(new BigDecimal(max));
        when(m.getPeriodCost()).thenReturn(cost == null ? null : new BigDecimal(cost));
        return m;
    }

    private static List<RecommendationCandidate> budgetRule(BudgetEvaluation evaluation) {
        return new BudgetOverrunRule().evaluate(context(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                List.of(evaluation), List.of()));
    }

    private static List<RecommendationCandidate> utilizationRule(ResourceUtilization... measurements) {
        return new LowUtilizationRule(DEFAULTS).evaluate(context(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                List.of(), List.of(measurements)));
    }

    @Test
    void exceededBudgetsAreHighPriority() {
        assertThat(budgetRule(budget("520.00", BudgetStatus.EXCEEDED, null))).singleElement().satisfies(c -> {
            assertThat(c.title()).isEqualTo("Budget exceeded: Production");
            assertThat(c.severity()).isEqualTo(Severity.HIGH);
            assertThat(c.evidenceSource()).isEqualTo(EvidenceSource.BUDGET_EVALUATION);
            assertThat(c.fingerprint()).isEqualTo("BUDGET_OVERRUN|7|2026-09-01|EXCEEDED");
        });
    }

    @Test
    void budgetsProjectedToExceedAreMediumPriorityAndExplainTheProjection() {
        assertThat(budgetRule(budget("410.00", BudgetStatus.WARNING, "615.00"))).singleElement().satisfies(c -> {
            assertThat(c.severity()).isEqualTo(Severity.MEDIUM);
            assertThat(c.evidence()).anySatisfy(line -> assertThat(line).contains("Linear run-rate projection"));
            assertThat(c.explanation()).contains("ignores planned changes");
        });
    }

    @Test
    void budgetsOnTrackProduceNothing() {
        assertThat(budgetRule(budget("200.00", BudgetStatus.NORMAL, "300.00"))).isEmpty();
    }

    @Test
    void lowCpuIsARightsizingReviewWithoutASavingsEstimate() {
        assertThat(utilizationRule(measurement("i-low", "4.20", "18.50", "138.24", 30))).singleElement()
                .satisfies(c -> {
                    assertThat(c.category()).isEqualTo(RecommendationCategory.RIGHTSIZING);
                    assertThat(c.evidenceSource()).isEqualTo(EvidenceSource.UTILIZATION_DATA);
                    assertThat(c.resourceId()).isEqualTo("i-low");
                    assertThat(c.estimatedMonthlySavings()).isNull();
                    assertThat(c.explanation()).contains("CPU alone does not establish");
                    assertThat(c.severity()).isEqualTo(Severity.MEDIUM);
                });
    }

    @Test
    void nearIdleResourcesGetAnUpperBoundSavingsEstimateFromTheirSuppliedCost() {
        assertThat(utilizationRule(measurement("i-idle", "1.10", "3.40", "56.00", 28))).singleElement()
                .satisfies(c -> {
                    assertThat(c.category()).isEqualTo(RecommendationCategory.IDLE_RESOURCE);
                    assertThat(c.estimatedMonthlySavings()).isEqualByComparingTo("60.00");
                    assertThat(c.savingsBasis()).startsWith("Upper bound");
                    assertThat(c.explanation()).contains("does not prove it is unused");
                });
    }

    @Test
    void nearIdleResourcesWithoutCostHaveNoEstimate() {
        assertThat(utilizationRule(measurement("i-idle", "1.10", "3.40", null, 30))).singleElement()
                .satisfies(c -> assertThat(c.estimatedMonthlySavings()).isNull());
    }

    @Test
    void busyResourcesShortPeriodsAndStaleMeasurementsAreIgnored() {
        assertThat(utilizationRule(measurement("i-busy", "54.20", "91.30", "276.48", 30))).isEmpty();
        assertThat(utilizationRule(measurement("i-bursty", "4.00", "85.00", "100.00", 30))).as("peaks").isEmpty();
        assertThat(utilizationRule(measurement("i-short", "1.00", "2.00", "10.00", 7))).isEmpty();

        ResourceUtilization stale = measurement("i-old", "1.00", "2.00", "10.00", 30);
        when(stale.getPeriodEnd()).thenReturn(LocalDate.of(2026, 5, 31));
        assertThat(utilizationRule(stale)).isEmpty();
    }
}
