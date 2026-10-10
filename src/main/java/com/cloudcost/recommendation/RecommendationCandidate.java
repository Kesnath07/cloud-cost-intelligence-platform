package com.cloudcost.recommendation;

import java.math.BigDecimal;
import java.util.List;

import com.cloudcost.common.DateRange;
import com.cloudcost.common.Severity;

/**
 * A recommendation produced by a rule.
 *
 * @param evidence     observed facts only (figures taken from the data)
 * @param explanation  interpretation of the facts, including what they do not prove
 * @param estimatedMonthlySavings only set when the evidence supports a figure; see {@code savingsBasis}
 */
public record RecommendationCandidate(
        String fingerprint,
        String ruleId,
        String title,
        RecommendationCategory category,
        Severity severity,
        String serviceName,
        String resourceId,
        String environment,
        EvidenceSource evidenceSource,
        List<String> evidence,
        String explanation,
        String suggestedAction,
        BigDecimal estimatedMonthlySavings,
        String savingsBasis,
        DateRange period) {

    public RecommendationCandidate {
        evidence = List.copyOf(evidence);
        if (evidence.isEmpty()) {
            throw new IllegalArgumentException("A recommendation needs evidence");
        }
        if (savingsBasis == null || savingsBasis.isBlank()) {
            throw new IllegalArgumentException("A recommendation must state the basis of its savings estimate");
        }
    }
}
