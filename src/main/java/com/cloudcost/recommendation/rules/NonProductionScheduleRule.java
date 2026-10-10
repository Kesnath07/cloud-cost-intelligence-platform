package com.cloudcost.recommendation.rules;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

import com.cloudcost.common.MoneyMath;
import com.cloudcost.common.Severity;
import com.cloudcost.recommendation.EvidenceSource;
import com.cloudcost.recommendation.RecommendationCandidate;
import com.cloudcost.recommendation.RecommendationCategory;
import com.cloudcost.recommendation.RecommendationContext;
import com.cloudcost.recommendation.RecommendationProperties;
import com.cloudcost.recommendation.RecommendationRule;

import org.springframework.stereotype.Component;

/**
 * Non-production environments whose weekend days cost nearly as much as weekdays. With daily
 * billing data this is the closest observable signal of resources running outside working
 * hours; it is presented as a prompt to review, not as proof of waste.
 */
@Component
public class NonProductionScheduleRule implements RecommendationRule {

    static final String ID = "NON_PRODUCTION_SCHEDULE";
    static final int MIN_WEEKDAYS = 10;
    static final int MIN_WEEKEND_DAYS = 4;

    private final RecommendationProperties properties;

    public NonProductionScheduleRule(RecommendationProperties properties) {
        this.properties = properties;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<RecommendationCandidate> evaluate(RecommendationContext context) {
        List<RecommendationCandidate> candidates = new ArrayList<>();
        for (Map.Entry<String, NavigableMap<LocalDate, BigDecimal>> entry : context.dailyByEnvironment().entrySet()) {
            String environment = entry.getKey();
            if (!properties.nonProductionEnvironments().contains(environment)) {
                continue;
            }
            BigDecimal weekdayTotal = BigDecimal.ZERO;
            BigDecimal weekendTotal = BigDecimal.ZERO;
            int weekdays = 0;
            int weekendDays = 0;
            for (Map.Entry<LocalDate, BigDecimal> day : entry.getValue().entrySet()) {
                if (isWeekend(day.getKey())) {
                    weekendTotal = weekendTotal.add(day.getValue());
                    weekendDays++;
                } else {
                    weekdayTotal = weekdayTotal.add(day.getValue());
                    weekdays++;
                }
            }
            if (weekdays < MIN_WEEKDAYS || weekendDays < MIN_WEEKEND_DAYS) {
                continue;
            }
            BigDecimal weekdayAverage = MoneyMath.average(weekdayTotal, weekdays);
            BigDecimal weekendAverage = MoneyMath.average(weekendTotal, weekendDays);
            if (weekdayAverage.signum() <= 0 || weekendAverage.compareTo(properties.minWeekendDailyCost()) < 0) {
                continue;
            }
            BigDecimal ratio = MoneyMath.percentOf(weekendAverage, weekdayAverage);
            if (ratio.compareTo(properties.weekendRatioPercent()) < 0) {
                continue;
            }
            Severity severity = weekendTotal.compareTo(BigDecimal.valueOf(1000)) >= 0 ? Severity.HIGH
                    : weekendTotal.compareTo(BigDecimal.valueOf(200)) >= 0 ? Severity.MEDIUM : Severity.LOW;
            List<String> evidence = List.of(
                    "Average weekday cost: " + context.money(weekdayAverage) + " over " + weekdays + " weekday(s).",
                    "Average weekend cost: " + context.money(weekendAverage) + " over " + weekendDays
                            + " weekend day(s), " + ratio.toPlainString() + "% of the weekday average.",
                    "Weekend spend in " + context.currentWindow() + ": " + context.money(weekendTotal) + ".");
            candidates.add(new RecommendationCandidate(ID + "|" + environment + "|" + context.month(), ID,
                    "Review always-on spending in " + environment, RecommendationCategory.SCHEDULING, severity, null,
                    null, environment, EvidenceSource.BILLING_DATA, evidence,
                    "Daily billing data cannot show hourly usage. Weekend costs close to weekday costs are consistent "
                            + "with resources running around the clock, but storage and other time-independent "
                            + "charges behave the same way, so this does not prove that anything is idle.",
                    "Identify compute resources in this environment (for example EC2 instances, RDS databases and "
                            + "ECS services) that are not needed outside working hours, and evaluate scheduled "
                            + "stop/start with their owners, for example with AWS Instance Scheduler.",
                    null, "Not estimated: daily billing data does not show which charges depend on running time.",
                    context.currentWindow()));
        }
        return candidates;
    }

    private static boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }
}
