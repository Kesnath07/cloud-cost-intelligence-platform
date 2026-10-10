package com.cloudcost.anomaly;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.cloudcost.analysis.CostDimension;
import com.cloudcost.common.Severity;

/** A day whose cost broke the anomaly rules, with the figures that support the finding. */
public record AnomalyFinding(
        CostDimension dimension,
        String dimensionValue,
        LocalDate date,
        BigDecimal observed,
        BigDecimal baseline,
        BigDecimal increase,
        BigDecimal increasePercent,
        int baselineDays,
        Severity severity,
        String reason) {
}
