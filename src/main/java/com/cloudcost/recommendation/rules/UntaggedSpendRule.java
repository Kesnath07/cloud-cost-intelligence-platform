package com.cloudcost.recommendation.rules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.cloudcost.common.MoneyMath;
import com.cloudcost.common.Severity;
import com.cloudcost.recommendation.EvidenceSource;
import com.cloudcost.recommendation.RecommendationCandidate;
import com.cloudcost.recommendation.RecommendationCategory;
import com.cloudcost.recommendation.RecommendationContext;
import com.cloudcost.recommendation.RecommendationProperties;
import com.cloudcost.recommendation.RecommendationRule;

import org.springframework.stereotype.Component;

/** A significant share of spending carries no environment tag and cannot be attributed. */
@Component
public class UntaggedSpendRule implements RecommendationRule {

    static final String ID = "UNTAGGED_SPEND";
    private static final int LISTED_SERVICES = 3;

    private final RecommendationProperties properties;

    public UntaggedSpendRule(RecommendationProperties properties) {
        this.properties = properties;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<RecommendationCandidate> evaluate(RecommendationContext context) {
        if (context.currentDaysWithData() < properties.minDaysWithData()) {
            return List.of();
        }
        BigDecimal untagged = context.untaggedByService().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal share = MoneyMath.share(untagged, context.currentTotal());
        if (share == null || share.compareTo(properties.untaggedSharePercent()) < 0
                || untagged.compareTo(properties.untaggedMinAmount()) < 0) {
            return List.of();
        }
        List<String> topServices = context.untaggedByService().entrySet().stream()
                .filter(entry -> entry.getValue().signum() > 0)
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(LISTED_SERVICES)
                .map(entry -> entry.getKey() + " (" + context.money(entry.getValue()) + ")")
                .toList();
        List<String> evidence = new ArrayList<>();
        evidence.add("Spend without an environment tag in " + context.currentWindow() + ": " + context.money(untagged)
                + " of " + context.money(context.currentTotal()) + " (" + share.toPlainString() + "%).");
        evidence.add("Largest untagged services: " + String.join(", ", topServices) + ".");
        Severity severity = share.compareTo(BigDecimal.valueOf(30)) >= 0 ? Severity.HIGH
                : share.compareTo(BigDecimal.valueOf(20)) >= 0 ? Severity.MEDIUM : Severity.LOW;
        return List.of(new RecommendationCandidate(ID + "|" + context.month(), ID,
                "Review costs without an environment tag (" + share.toPlainString() + "% of spend)",
                RecommendationCategory.TAGGING, severity, null, null, "", EvidenceSource.BILLING_DATA, evidence,
                "Untagged costs cannot be attributed to an environment or team, which hides them from environment "
                        + "budgets and from the non-production scheduling review. Some charges cannot be tagged at "
                        + "all (for example some data transfer, support and tax), so a small untagged share is normal.",
                "Tag the resources behind the listed services with the environment tag, activate it as a cost "
                        + "allocation tag in the AWS Billing console, and consider tag policies in AWS Organizations "
                        + "to enforce it. Tags only apply to costs incurred after activation.",
                null, "Not applicable: tagging improves cost visibility rather than reducing cost directly.",
                context.currentWindow()));
    }
}
