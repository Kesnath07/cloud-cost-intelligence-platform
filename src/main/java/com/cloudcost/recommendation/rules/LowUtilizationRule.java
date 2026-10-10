package com.cloudcost.recommendation.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import com.cloudcost.common.DateRange;
import com.cloudcost.common.Severity;
import com.cloudcost.costdata.ResourceUtilization;
import com.cloudcost.recommendation.EvidenceSource;
import com.cloudcost.recommendation.RecommendationCandidate;
import com.cloudcost.recommendation.RecommendationCategory;
import com.cloudcost.recommendation.RecommendationContext;
import com.cloudcost.recommendation.RecommendationProperties;
import com.cloudcost.recommendation.RecommendationRule;

import org.springframework.stereotype.Component;

/**
 * Resources with measured low CPU utilization. This is the only rule that discusses individual
 * resources, and it only does so for resources with supplied utilization measurements, because
 * billing data alone cannot show whether a resource is busy.
 */
@Component
public class LowUtilizationRule implements RecommendationRule {

    static final String RIGHTSIZING_ID = "LOW_CPU_UTILIZATION";
    static final String IDLE_ID = "NEAR_IDLE_RESOURCE";
    private static final BigDecimal DAYS_PER_MONTH = BigDecimal.valueOf(30);

    private final RecommendationProperties properties;

    public LowUtilizationRule(RecommendationProperties properties) {
        this.properties = properties;
    }

    @Override
    public String id() {
        return RIGHTSIZING_ID;
    }

    @Override
    public List<RecommendationCandidate> evaluate(RecommendationContext context) {
        List<RecommendationCandidate> candidates = new ArrayList<>();
        for (ResourceUtilization measurement : context.utilization()) {
            if (measurement.periodDays() < properties.minUtilizationDays()
                    || measurement.getPeriodEnd().isBefore(context.asOf().minusDays(properties.maxUtilizationAgeDays()))) {
                continue;
            }
            boolean idle = measurement.getMaxCpuPercent().compareTo(properties.idleCpuPeakPercent()) <= 0;
            boolean low = measurement.getAvgCpuPercent().compareTo(properties.lowCpuAveragePercent()) < 0
                    && measurement.getMaxCpuPercent().compareTo(properties.lowCpuPeakPercent()) < 0;
            if (idle) {
                candidates.add(idleCandidate(context, measurement));
            } else if (low) {
                candidates.add(rightsizingCandidate(context, measurement));
            }
        }
        return candidates;
    }

    private RecommendationCandidate rightsizingCandidate(RecommendationContext context, ResourceUtilization m) {
        return new RecommendationCandidate(fingerprint(RIGHTSIZING_ID, m), RIGHTSIZING_ID,
                "Low CPU utilization: " + m.getResourceId(), RecommendationCategory.RIGHTSIZING,
                severity(monthlyCost(m)), m.getServiceName(), m.getResourceId(), m.getEnvironment(),
                EvidenceSource.UTILIZATION_DATA, evidence(context, m),
                "Measured CPU stayed low for the whole period. CPU alone does not establish that the resource is "
                        + "oversized: memory, network, disk I/O and burst requirements were not measured here.",
                "Check memory and peak-load metrics with the owning team, then consider a smaller size or a "
                        + "different instance family. Test the change outside production first.",
                null, "Not estimated: savings depend on the target size, which needs memory and peak-load analysis.",
                DateRange.of(m.getPeriodStart(), m.getPeriodEnd()));
    }

    private RecommendationCandidate idleCandidate(RecommendationContext context, ResourceUtilization m) {
        BigDecimal monthlyCost = monthlyCost(m);
        String basis = monthlyCost == null
                ? "Not estimated: no cost was supplied for this resource."
                : "Upper bound: the supplied cost for " + m.getPeriodStart() + " to " + m.getPeriodEnd()
                        + " normalized to 30 days. Realized only if the resource is confirmed unneeded and removed.";
        return new RecommendationCandidate(fingerprint(IDLE_ID, m), IDLE_ID,
                "Near-idle resource: " + m.getResourceId(), RecommendationCategory.IDLE_RESOURCE,
                severity(monthlyCost), m.getServiceName(), m.getResourceId(), m.getEnvironment(),
                EvidenceSource.UTILIZATION_DATA, evidence(context, m),
                "Peak CPU never exceeded " + properties.idleCpuPeakPercent().stripTrailingZeros().toPlainString()
                        + "% during the period, so the resource did little processing. That does not prove it is "
                        + "unused: it may hold data, serve rare requests or act as a standby.",
                "Confirm with the owner whether the resource is still needed. If it is not, back it up as "
                        + "appropriate and remove it; if it is needed only occasionally, consider scheduling it.",
                monthlyCost, basis, DateRange.of(m.getPeriodStart(), m.getPeriodEnd()));
    }

    private static List<String> evidence(RecommendationContext context, ResourceUtilization m) {
        List<String> evidence = new ArrayList<>();
        evidence.add("Average CPU " + m.getAvgCpuPercent().toPlainString() + "%, peak CPU "
                + m.getMaxCpuPercent().toPlainString() + "% from " + m.getPeriodStart() + " to " + m.getPeriodEnd()
                + " (" + m.periodDays() + " days).");
        String type = m.getResourceType().isEmpty() ? "" : m.getResourceType() + ", ";
        evidence.add("Resource: " + type + m.getServiceName()
                + (m.getEnvironment().isEmpty() ? ", untagged" : ", " + m.getEnvironment()) + ".");
        if (m.getPeriodCost() != null) {
            evidence.add("Supplied cost for the period: " + context.money(m.getPeriodCost()) + ".");
        }
        return evidence;
    }

    private static BigDecimal monthlyCost(ResourceUtilization m) {
        if (m.getPeriodCost() == null) {
            return null;
        }
        return m.getPeriodCost().multiply(DAYS_PER_MONTH)
                .divide(BigDecimal.valueOf(m.periodDays()), 2, RoundingMode.HALF_UP);
    }

    private static Severity severity(BigDecimal monthlyCost) {
        if (monthlyCost == null) {
            return Severity.LOW;
        }
        return monthlyCost.compareTo(BigDecimal.valueOf(500)) >= 0 ? Severity.HIGH
                : monthlyCost.compareTo(BigDecimal.valueOf(100)) >= 0 ? Severity.MEDIUM : Severity.LOW;
    }

    private static String fingerprint(String ruleId, ResourceUtilization m) {
        return ruleId + "|" + m.getResourceId() + "|" + m.getPeriodEnd();
    }
}
