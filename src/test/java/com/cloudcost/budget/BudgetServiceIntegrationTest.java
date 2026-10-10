package com.cloudcost.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.cloudcost.ingestion.CostIngestionService;
import com.cloudcost.support.CostDataFixture;
import com.cloudcost.support.DatabaseCleaner;
import com.cloudcost.support.PostgresIntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@PostgresIntegrationTest
class BudgetServiceIntegrationTest {

    @Autowired
    private BudgetService budgets;

    @Autowired
    private CostIngestionService ingestion;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.clean(jdbc);
        StringBuilder rows = new StringBuilder();
        // 41.00 per day in September for production compute, plus noise outside the scope.
        for (int day = 1; day <= 10; day++) {
            rows.append("2026-09-%02d,EC2,41.00,USD,production,USAGE%n".formatted(day));
            rows.append("2026-09-%02d,S3,5.00,USD,staging,USAGE%n".formatted(day));
        }
        rows.append("2026-08-31,EC2,999.00,USD,production,USAGE\n");
        CostDataFixture.importRows(ingestion, rows.toString());
    }

    private static BudgetForm form(String name, String limit, String service, String environment) {
        BudgetForm form = new BudgetForm();
        form.setName(name);
        form.setLimitAmount(new BigDecimal(limit));
        form.setPeriod(BudgetPeriod.MONTHLY);
        form.setService(service);
        form.setEnvironment(environment);
        form.setWarningThresholdPercent(80);
        return form;
    }

    @Test
    void evaluatesScopedSpendForTheCurrentPeriodOnly() {
        CostBudget budget = budgets.create(form("Production compute", "500.00",
                "Amazon Elastic Compute Cloud - Compute", "production"));

        BudgetEvaluation evaluation = budgets.evaluate(budget, LocalDate.of(2026, 9, 10));

        assertThat(evaluation.spend()).isEqualByComparingTo("410.00");
        assertThat(evaluation.utilizationPercent()).isEqualByComparingTo("82.00");
        assertThat(evaluation.status()).isEqualTo(BudgetStatus.WARNING);
        assertThat(evaluation.remaining()).isEqualByComparingTo("90.00");
        assertThat(evaluation.daysElapsed()).isEqualTo(10);
        assertThat(evaluation.daysInPeriod()).isEqualTo(30);
        assertThat(evaluation.projectedSpend()).isEqualByComparingTo("1230.00");
        assertThat(evaluation.projectedToExceed()).isTrue();
    }

    @Test
    void transitionsFromNormalToWarningToExceededAsSpendingAccumulates() {
        // All spending is 46.00 per day; the August outlier belongs to the previous period.
        CostBudget budget = budgets.create(form("Everything", "450.00", null, null));

        assertThat(budgets.evaluate(budget, LocalDate.of(2026, 9, 5)).status()).isEqualTo(BudgetStatus.NORMAL);
        assertThat(budgets.evaluate(budget, LocalDate.of(2026, 9, 7)).status()).as("322.00 = 71.6%")
                .isEqualTo(BudgetStatus.NORMAL);
        assertThat(budgets.evaluate(budget, LocalDate.of(2026, 9, 8)).status()).as("368.00 = 81.8%")
                .isEqualTo(BudgetStatus.WARNING);
        assertThat(budgets.evaluate(budget, LocalDate.of(2026, 9, 10)).status()).as("460.00")
                .isEqualTo(BudgetStatus.EXCEEDED);
    }

    @Test
    void doesNotProjectFromFewerThanSevenDays() {
        CostBudget budget = budgets.create(form("Everything", "5000.00", null, null));

        assertThat(budgets.evaluate(budget, LocalDate.of(2026, 9, 6)).projectedSpend()).isNull();
        assertThat(budgets.evaluate(budget, LocalDate.of(2026, 9, 7)).projectedSpend()).isNotNull();
    }

    @Test
    void untaggedScopeOnlyCountsCostsWithoutAnEnvironmentTag() {
        CostBudget budget = budgets.create(form("Untagged", "100.00", null, "(untagged)"));

        assertThat(budget.getEnvironment()).isEmpty();
        assertThat(budgets.evaluate(budget, LocalDate.of(2026, 9, 10)).spend()).isEqualByComparingTo("0");
    }

    @Test
    void budgetNamesAreUniqueIgnoringCase() {
        budgets.create(form("Production", "100.00", null, null));

        assertThatThrownBy(() -> budgets.create(form("  PRODUCTION ", "200.00", null, null)))
                .isInstanceOf(DuplicateBudgetNameException.class);
    }

    @Test
    void defaultEvaluationDateIsTheLatestStoredDataDate() {
        assertThat(budgets.defaultAsOf()).isEqualTo(LocalDate.of(2026, 9, 10));
    }
}
