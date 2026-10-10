package com.cloudcost.budget;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import com.cloudcost.common.DateRange;

import org.junit.jupiter.api.Test;

class BudgetPeriodTest {

    @Test
    void monthlyPeriodsFollowCalendarMonthsIncludingLeapYears() {
        assertThat(BudgetPeriod.MONTHLY.periodContaining(LocalDate.of(2028, 2, 15)))
                .isEqualTo(DateRange.of(LocalDate.of(2028, 2, 1), LocalDate.of(2028, 2, 29)));
    }

    @Test
    void quarterlyPeriodsStartInJanuaryAprilJulyAndOctober() {
        assertThat(BudgetPeriod.QUARTERLY.periodContaining(LocalDate.of(2026, 9, 30)))
                .isEqualTo(DateRange.of(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30)));
        assertThat(BudgetPeriod.QUARTERLY.periodContaining(LocalDate.of(2026, 10, 1)))
                .isEqualTo(DateRange.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31)));
    }

    @Test
    void annualPeriodsAreCalendarYears() {
        assertThat(BudgetPeriod.ANNUAL.periodContaining(LocalDate.of(2026, 6, 15)))
                .isEqualTo(DateRange.of(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
    }
}
