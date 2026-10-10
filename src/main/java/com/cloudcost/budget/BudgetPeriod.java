package com.cloudcost.budget;

import java.time.LocalDate;
import java.time.YearMonth;

import com.cloudcost.common.DateRange;

/** Recurring calendar periods a budget can apply to. Periods follow UTC calendar days. */
public enum BudgetPeriod {
    MONTHLY("Monthly"),
    QUARTERLY("Quarterly"),
    ANNUAL("Annual");

    private final String label;

    BudgetPeriod(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** The calendar period of this type that contains {@code date}. */
    public DateRange periodContaining(LocalDate date) {
        return switch (this) {
            case MONTHLY -> {
                YearMonth month = YearMonth.from(date);
                yield DateRange.of(month.atDay(1), month.atEndOfMonth());
            }
            case QUARTERLY -> {
                int firstMonth = (date.getMonthValue() - 1) / 3 * 3 + 1;
                LocalDate start = LocalDate.of(date.getYear(), firstMonth, 1);
                yield DateRange.of(start, start.plusMonths(3).minusDays(1));
            }
            case ANNUAL -> DateRange.of(LocalDate.of(date.getYear(), 1, 1), LocalDate.of(date.getYear(), 12, 31));
        };
    }
}
