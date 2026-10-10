package com.cloudcost.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BudgetEvaluatorTest {

    private static final BigDecimal LIMIT = new BigDecimal("500.00");

    @ParameterizedTest(name = "spend {0} with warning at {1}% -> {2}")
    @CsvSource({
            "0, 80, NORMAL",
            "-25.00, 80, NORMAL",
            "399.99, 80, NORMAL",
            "400.00, 80, WARNING",
            "410.00, 80, WARNING",
            "499.99, 80, WARNING",
            "500.00, 80, EXCEEDED",
            "750.00, 80, EXCEEDED",
            "499.99, 100, NORMAL",
            "500.00, 100, EXCEEDED",
            "5.00, 1, WARNING"
    })
    void statusChangesExactlyAtTheConfiguredThresholds(String spend, int warningPercent, BudgetStatus expected) {
        assertThat(BudgetEvaluator.status(new BigDecimal(spend), LIMIT, warningPercent)).isEqualTo(expected);
    }

    @Test
    void utilizationIsSpendDividedByLimit() {
        assertThat(BudgetEvaluator.utilizationPercent(new BigDecimal("410"), LIMIT)).isEqualByComparingTo("82.00");
        assertThat(BudgetEvaluator.utilizationPercent(new BigDecimal("1"), new BigDecimal("3")))
                .isEqualByComparingTo("33.33");
        assertThat(BudgetEvaluator.utilizationPercent(new BigDecimal("-50"), LIMIT)).isEqualByComparingTo("-10.00");
    }

    @Test
    void displayRoundingDoesNotAffectTheStatus() {
        // 399.99 / 500 = 79.998%, displayed as 80.00%, but still below the 80% threshold.
        assertThat(BudgetEvaluator.utilizationPercent(new BigDecimal("399.99"), LIMIT)).isEqualByComparingTo("80.00");
        assertThat(BudgetEvaluator.status(new BigDecimal("399.99"), LIMIT, 80)).isEqualTo(BudgetStatus.NORMAL);
    }

    @Test
    void rejectsInvalidLimitsAndThresholds() {
        assertThatThrownBy(() -> BudgetEvaluator.status(BigDecimal.ONE, BigDecimal.ZERO, 80))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BudgetEvaluator.status(BigDecimal.ONE, LIMIT, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BudgetEvaluator.status(BigDecimal.ONE, LIMIT, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
