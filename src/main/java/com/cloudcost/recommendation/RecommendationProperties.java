package com.cloudcost.recommendation;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Thresholds of the recommendation rules. See docs/cost-analysis.md for how each is used.
 *
 * @param windowDays                  length of the analysis window ending on the latest data day
 * @param minDaysWithData             days with data required in a window before billing rules run
 * @param sustainedIncreasePercent    minimum period-over-period increase for a service
 * @param sustainedIncreaseAmount     minimum absolute increase for a service
 * @param nonProductionEnvironments   environment values reviewed for always-on spending
 * @param weekendRatioPercent         weekend/weekday average daily cost ratio that triggers a review
 * @param minWeekendDailyCost         ignore environments whose average weekend day costs less
 * @param untaggedSharePercent        share of untagged spend that triggers a tagging review
 * @param untaggedMinAmount           minimum untagged spend in the window
 * @param lowCpuAveragePercent        average CPU below which a resource is a rightsizing candidate
 * @param lowCpuPeakPercent           peak CPU that must also not be exceeded
 * @param idleCpuPeakPercent          peak CPU at or below which a resource is near idle
 * @param minUtilizationDays          shortest measurement period that is considered
 * @param maxUtilizationAgeDays       measurements ending earlier than this before the analysis date are ignored
 */
@Validated
@ConfigurationProperties("costplatform.recommendation")
public record RecommendationProperties(
        @DefaultValue("30") @Min(14) @Max(180) int windowDays,
        @DefaultValue("14") @Min(1) int minDaysWithData,
        @DefaultValue("20") @NotNull @DecimalMin("1") BigDecimal sustainedIncreasePercent,
        @DefaultValue("100.00") @NotNull @DecimalMin("0.01") BigDecimal sustainedIncreaseAmount,
        @DefaultValue({"development", "staging", "test", "qa", "sandbox"}) List<String> nonProductionEnvironments,
        @DefaultValue("80") @NotNull @DecimalMin("1") @DecimalMax("200") BigDecimal weekendRatioPercent,
        @DefaultValue("5.00") @NotNull @DecimalMin("0") BigDecimal minWeekendDailyCost,
        @DefaultValue("10") @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal untaggedSharePercent,
        @DefaultValue("50.00") @NotNull @DecimalMin("0") BigDecimal untaggedMinAmount,
        @DefaultValue("10") @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal lowCpuAveragePercent,
        @DefaultValue("40") @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal lowCpuPeakPercent,
        @DefaultValue("5") @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal idleCpuPeakPercent,
        @DefaultValue("14") @Min(1) int minUtilizationDays,
        @DefaultValue("90") @Min(1) int maxUtilizationAgeDays) {
}
