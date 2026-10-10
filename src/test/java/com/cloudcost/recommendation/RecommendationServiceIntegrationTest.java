package com.cloudcost.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import com.cloudcost.budget.BudgetForm;
import com.cloudcost.budget.BudgetPeriod;
import com.cloudcost.budget.BudgetService;
import com.cloudcost.ingestion.CostIngestionService;
import com.cloudcost.support.DatabaseCleaner;
import com.cloudcost.support.PostgresIntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@PostgresIntegrationTest
class RecommendationServiceIntegrationTest {

    @Autowired
    private RecommendationService service;

    @Autowired
    private OptimizationRecommendationRepository recommendations;

    @Autowired
    private CostIngestionService ingestion;

    @Autowired
    private BudgetService budgets;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        DatabaseCleaner.clean(jdbc);
    }

    private List<String> openTitles() {
        return recommendations.findAll().stream()
                .filter(r -> r.getStatus() == RecommendationStatus.OPEN)
                .map(OptimizationRecommendation::getTitle)
                .sorted()
                .toList();
    }

    @Test
    void producesNothingWithoutData() {
        assertThat(service.generate().candidates()).isZero();
    }

    @Test
    void demonstrationDatasetProducesEvidenceBasedRecommendations() {
        ingestion.loadDemoDataset();

        assertThat(openTitles()).containsExactly(
                "Low CPU utilization: demo-reporting-db",
                "Low CPU utilization: i-0demo00000000002",
                "Near-idle resource: i-0demo00000000003",
                "Review always-on spending in development",
                "Review costs without an environment tag (10.85% of spend)",
                "Sustained cost increase: Amazon Relational Database Service (+27.01%)");
        assertThat(recommendations.findAll()).allSatisfy(r -> {
            assertThat(r.evidenceLines()).isNotEmpty();
            assertThat(r.getSavingsBasis()).isNotBlank();
            assertThat(r.getCreatedAt()).isNotNull();
            if (r.getEstimatedMonthlySavings() != null) {
                assertThat(r.getCategory()).isEqualTo(RecommendationCategory.IDLE_RESOURCE);
            }
        });
    }

    @Test
    void regenerationKeepsReviewedRecommendationsAndDoesNotDuplicate() {
        ingestion.loadDemoDataset();
        OptimizationRecommendation tagging = recommendations.findAll().stream()
                .filter(r -> r.getCategory() == RecommendationCategory.TAGGING).findFirst().orElseThrow();
        service.changeStatus(tagging.getId(), RecommendationStatus.DISMISSED, "Shared networking, accepted");
        long count = recommendations.count();

        RecommendationService.GenerationResult result = service.generate();

        assertThat(result.created()).isZero();
        assertThat(recommendations.count()).isEqualTo(count);
        assertThat(recommendations.findById(tagging.getId())).get()
                .satisfies(r -> assertThat(r.getStatus()).isEqualTo(RecommendationStatus.DISMISSED));
    }

    @Test
    void openRecommendationsDisappearWhenTheirEvidenceIsRemoved() {
        ingestion.loadDemoDataset();

        ingestion.removeDemoData();

        assertThat(recommendations.count()).isZero();
    }

    @Test
    void budgetChangesTriggerReevaluation() {
        ingestion.loadDemoDataset();
        BudgetForm form = new BudgetForm();
        form.setName("Production monthly");
        form.setLimitAmount(new BigDecimal("5000.00"));
        form.setPeriod(BudgetPeriod.MONTHLY);
        form.setEnvironment("production");
        form.setWarningThresholdPercent(80);

        budgets.create(form);

        assertThat(openTitles()).contains("Budget exceeded: Production monthly");
    }
}
