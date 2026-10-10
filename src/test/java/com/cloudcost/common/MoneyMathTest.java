package com.cloudcost.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyMathTest {

    @ParameterizedTest(name = "{1} -> {0} = {2}%")
    @CsvSource({
            "150, 100, 50.00",
            "50, 100, -50.00",
            "100, 100, 0.00",
            "0.10, 0.03, 233.33",
            "100.005, 100, 0.01",
            // Baseline of net credits: the change is measured against the magnitude of the baseline.
            "-5, -10, 50.00",
            "10, -10, 200.00"
    })
    void percentChangeIsRelativeToTheMagnitudeOfThePreviousValue(String current, String previous, String expected) {
        assertThat(MoneyMath.percentChange(new BigDecimal(current), new BigDecimal(previous)))
                .isEqualByComparingTo(expected);
    }

    @Test
    void percentChangeIsUndefinedWithoutABaseline() {
        assertThat(MoneyMath.percentChange(new BigDecimal("25"), BigDecimal.ZERO)).isNull();
        assertThat(MoneyMath.percentChange(new BigDecimal("25"), null)).isNull();
        assertThat(MoneyMath.percentChange(BigDecimal.ZERO, new BigDecimal("0.000000"))).isNull();
    }

    @Test
    void shareIsUndefinedForZeroOrNegativeTotals() {
        assertThat(MoneyMath.share(new BigDecimal("25"), new BigDecimal("200"))).isEqualByComparingTo("12.50");
        assertThat(MoneyMath.share(new BigDecimal("1"), new BigDecimal("3"))).isEqualByComparingTo("33.33");
        assertThat(MoneyMath.share(new BigDecimal("25"), BigDecimal.ZERO)).isNull();
        assertThat(MoneyMath.share(new BigDecimal("25"), new BigDecimal("-5"))).isNull();
    }

    @Test
    void averagesKeepSixDecimalPlacesAndRejectEmptyCounts() {
        assertThat(MoneyMath.average(new BigDecimal("10"), 3)).isEqualByComparingTo("3.333333");
        assertThat(MoneyMath.average(new BigDecimal("10"), 0)).isNull();
    }

    @Test
    void decimalArithmeticDoesNotAccumulateFloatingPointError() {
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < 10; i++) {
            total = total.add(new BigDecimal("0.1"));
        }
        assertThat(total).isEqualByComparingTo("1.0");
        assertThat(MoneyMath.percentOf(total, new BigDecimal("4"))).isEqualByComparingTo("25.00");
    }
}
