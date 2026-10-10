package com.cloudcost.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cost of one day. {@code hasData} is false for days with no ingested data at all (a gap),
 * in which case {@code amount} is {@code null} rather than a misleading zero.
 */
public record DailyCost(LocalDate date, BigDecimal amount, boolean hasData) {
}
