package com.cloudcost.ingestion;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A validated utilization measurement ready to be stored. */
public record NormalizedUtilization(
        String resourceId,
        String resourceType,
        String serviceName,
        String environment,
        String region,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal avgCpuPercent,
        BigDecimal maxCpuPercent,
        BigDecimal periodCost,
        String currency) {
}
