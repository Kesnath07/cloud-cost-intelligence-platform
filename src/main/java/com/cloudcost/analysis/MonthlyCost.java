package com.cloudcost.analysis;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * Cost of one calendar month within an analysis range. {@code partial} is true when the range
 * covers only part of the month, so the amount is not a full-month figure.
 */
public record MonthlyCost(YearMonth month, BigDecimal amount, int daysWithData, int daysCovered, boolean partial) {
}
