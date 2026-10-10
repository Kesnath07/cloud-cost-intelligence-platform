package com.cloudcost.anomaly;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Thresholds for rule-based anomaly detection. A day is flagged only when its cost exceeds the
 * baseline by at least {@code minPercentIncrease} percent <em>and</em> by at least
 * {@code minAbsoluteIncrease}, so that large relative changes on tiny amounts are ignored.
 *
 * @param baselineDays          calendar days before the evaluated day that form the baseline window
 * @param minBaselineDays       minimum number of days with data in the window to evaluate a day
 * @param minPercentIncrease    minimum increase over the baseline, in percent
 * @param minAbsoluteIncrease   minimum increase over the baseline, in the reporting currency
 * @param mediumIncrease        absolute increase at which severity becomes MEDIUM
 * @param highIncrease          absolute increase at which severity becomes HIGH
 */
@Validated
@ConfigurationProperties("costplatform.anomaly")
public record AnomalyProperties(
        @DefaultValue("14") @Min(3) @Max(90) int baselineDays,
        @DefaultValue("7") @Min(2) @Max(90) int minBaselineDays,
        @DefaultValue("50") @NotNull @DecimalMin("1") BigDecimal minPercentIncrease,
        @DefaultValue("25.00") @NotNull @DecimalMin("0.01") BigDecimal minAbsoluteIncrease,
        @DefaultValue("100.00") @NotNull @DecimalMin("0.01") BigDecimal mediumIncrease,
        @DefaultValue("500.00") @NotNull @DecimalMin("0.01") BigDecimal highIncrease) {

    public AnomalyProperties {
        if (minBaselineDays > baselineDays) {
            throw new IllegalArgumentException("min-baseline-days cannot exceed baseline-days");
        }
        if (mediumIncrease.compareTo(highIncrease) > 0) {
            throw new IllegalArgumentException("medium-increase cannot exceed high-increase");
        }
    }
}
