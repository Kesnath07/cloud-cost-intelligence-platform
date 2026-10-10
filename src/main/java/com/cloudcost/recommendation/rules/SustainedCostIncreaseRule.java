package com.cloudcost.recommendation.rules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NavigableMap;
import java.util.Set;

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
 * Services whose spend rose by at least the configured percentage and amount compared with the
 * previous window, where the increase is visible in both halves of the current window. The
 * "both halves" condition separates lasting increases from one-off spikes, which the anomaly
 * detector reports instead.
 */
@Component
public class SustainedCostIncreaseRule implements RecommendationRule {

    static final String ID = "SUSTAINED_INCREASE";

    private final RecommendationProperties properties;

    public SustainedCostIncreaseRule(RecommendationProperties properties) {
        this.properties = properties;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<RecommendationCandidate> evaluate(RecommendationContext context) {
        if (context.currentDaysWithData() < properties.minDaysWithData()
                || context.previousDaysWithData() < properties.minDaysWithData()) {
            return List.of();
        }
        Set<String> services = new LinkedHashSet<>(context.currentByService().keySet());
        List<RecommendationCandidate> candidates = new ArrayList<>();
        for (String service : services) {
            BigDecimal current = context.currentByService().getOrDefault(service, BigDecimal.ZERO);
            BigDecimal previous = context.previousByService().getOrDefault(service, BigDecimal.ZERO);
            if (previous.signum() <= 0) {
                continue;
            }
            BigDecimal change = current.subtract(previous);
            BigDecimal percent = MoneyMath.percentChange(current, previous);
            if (change.compareTo(properties.sustainedIncreaseAmount()) < 0
                    || percent.compareTo(properties.sustainedIncreasePercent()) < 0) {
                continue;
            }
            BigDecimal previousDaily = MoneyMath.average(previous, context.previousDaysWithData());
            BigDecimal threshold = previousDaily.multiply(BigDecimal.ONE.add(properties.sustainedIncreasePercent()
                    .movePointLeft(2)));
            NavigableMap<LocalDate, BigDecimal> daily = context.dailyByService().get(service);
            LocalDate middle = context.currentWindow().start().plusDays(context.currentWindow().days() / 2);
            BigDecimal firstHalf = averageOf(daily.headMap(middle, false));
            BigDecimal secondHalf = averageOf(daily.tailMap(middle, true));
            if (firstHalf == null || secondHalf == null
                    || firstHalf.compareTo(threshold) < 0 || secondHalf.compareTo(threshold) < 0) {
                continue;
            }
            candidates.add(candidate(context, service, current, previous, change, percent, previousDaily, firstHalf,
                    secondHalf));
        }
        return candidates;
    }

    private RecommendationCandidate candidate(RecommendationContext context, String service, BigDecimal current,
            BigDecimal previous, BigDecimal change, BigDecimal percent, BigDecimal previousDaily,
            BigDecimal firstHalf, BigDecimal secondHalf) {
        BigDecimal minimum = properties.sustainedIncreaseAmount();
        Severity severity = change.compareTo(minimum.multiply(BigDecimal.TEN)) >= 0 ? Severity.HIGH
                : change.compareTo(minimum.multiply(BigDecimal.valueOf(3))) >= 0 ? Severity.MEDIUM : Severity.LOW;
        List<String> evidence = List.of(
                "Spend " + context.currentWindow() + ": " + context.money(current) + "; " + context.previousWindow()
                        + ": " + context.money(previous) + " (change +" + context.money(change) + ", +"
                        + percent.toPlainString() + "%).",
                "Average daily spend: " + context.money(previousDaily) + " in the previous window, "
                        + context.money(firstHalf) + " in the first half and " + context.money(secondHalf)
                        + " in the second half of the current window.");
        return new RecommendationCandidate(ID + "|" + service + "|" + context.month(), ID,
                "Sustained cost increase: " + service + " (+" + percent.toPlainString() + "%)",
                RecommendationCategory.COST_INCREASE, severity, service, null, null, EvidenceSource.BILLING_DATA,
                evidence,
                "The higher spending persists through the whole current window rather than coming from a single "
                        + "spike. Billing data shows how much more is spent, not why: possible causes include higher "
                        + "usage, new or larger resources, data transfer, or pricing changes.",
                "In AWS Cost Explorer, filter to this service and group by usage type (and by resource, if "
                        + "resource-level data is enabled) to find what grew. Confirm with the owning team whether "
                        + "the growth is expected before acting.",
                null, "Not estimated: billing data does not show which part of the increase is avoidable.",
                context.currentWindow());
    }

    private static BigDecimal averageOf(NavigableMap<LocalDate, BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return MoneyMath.average(values.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add), values.size());
    }
}
