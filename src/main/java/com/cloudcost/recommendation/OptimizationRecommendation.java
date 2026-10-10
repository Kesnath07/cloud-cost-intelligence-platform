package com.cloudcost.recommendation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import com.cloudcost.common.Severity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "optimization_recommendation")
public class OptimizationRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String fingerprint;

    @Column(name = "rule_id", nullable = false, length = 64)
    private String ruleId;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RecommendationCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Severity severity;

    @Column(name = "service_name", length = 128)
    private String serviceName;

    @Column(name = "resource_id", length = 256)
    private String resourceId;

    @Column(length = 64)
    private String environment;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_source", nullable = false, length = 32)
    private EvidenceSource evidenceSource;

    /** Observed facts, one per line. */
    @Column(nullable = false, columnDefinition = "text")
    private String evidence;

    @Column(nullable = false, columnDefinition = "text")
    private String explanation;

    @Column(name = "suggested_action", nullable = false, columnDefinition = "text")
    private String suggestedAction;

    @Column(name = "estimated_monthly_savings", precision = 19, scale = 2)
    private BigDecimal estimatedMonthlySavings;

    @Column(name = "savings_basis", nullable = false, length = 500)
    private String savingsBasis;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RecommendationStatus status;

    @Column(name = "status_note", length = 500)
    private String statusNote;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_evaluated_at", nullable = false)
    private Instant lastEvaluatedAt;

    protected OptimizationRecommendation() {
    }

    public static OptimizationRecommendation open(RecommendationCandidate candidate, Instant now) {
        OptimizationRecommendation recommendation = new OptimizationRecommendation();
        recommendation.fingerprint = candidate.fingerprint();
        recommendation.ruleId = candidate.ruleId();
        recommendation.status = RecommendationStatus.OPEN;
        recommendation.createdAt = now;
        recommendation.refresh(candidate, now);
        return recommendation;
    }

    /** Replaces the evidence with the latest evaluation; status and review notes are kept. */
    public void refresh(RecommendationCandidate candidate, Instant now) {
        this.title = candidate.title();
        this.category = candidate.category();
        this.severity = candidate.severity();
        this.serviceName = candidate.serviceName();
        this.resourceId = candidate.resourceId();
        this.environment = candidate.environment();
        this.evidenceSource = candidate.evidenceSource();
        this.evidence = String.join("\n", candidate.evidence());
        this.explanation = candidate.explanation();
        this.suggestedAction = candidate.suggestedAction();
        this.estimatedMonthlySavings = candidate.estimatedMonthlySavings();
        this.savingsBasis = candidate.savingsBasis();
        this.periodStart = candidate.period() == null ? null : candidate.period().start();
        this.periodEnd = candidate.period() == null ? null : candidate.period().end();
        this.updatedAt = now;
        this.lastEvaluatedAt = now;
    }

    public void changeStatus(RecommendationStatus newStatus, String note, Instant now) {
        this.status = newStatus;
        this.statusNote = note == null || note.isBlank() ? null : note.strip();
        this.statusChangedAt = now;
        this.updatedAt = now;
    }

    public List<String> evidenceLines() {
        return Arrays.asList(evidence.split("\n"));
    }

    public Long getId() {
        return id;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getTitle() {
        return title;
    }

    public RecommendationCategory getCategory() {
        return category;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getEnvironment() {
        return environment;
    }

    public EvidenceSource getEvidenceSource() {
        return evidenceSource;
    }

    public String getEvidence() {
        return evidence;
    }

    public String getExplanation() {
        return explanation;
    }

    public String getSuggestedAction() {
        return suggestedAction;
    }

    public BigDecimal getEstimatedMonthlySavings() {
        return estimatedMonthlySavings;
    }

    public String getSavingsBasis() {
        return savingsBasis;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public RecommendationStatus getStatus() {
        return status;
    }

    public String getStatusNote() {
        return statusNote;
    }

    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getLastEvaluatedAt() {
        return lastEvaluatedAt;
    }
}
