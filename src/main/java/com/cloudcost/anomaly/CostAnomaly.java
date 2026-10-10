package com.cloudcost.anomaly;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.cloudcost.analysis.CostDimension;
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
@Table(name = "cost_anomaly")
public class CostAnomaly {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CostDimension dimension;

    @Column(name = "dimension_value", nullable = false, length = 128)
    private String dimensionValue;

    @Column(name = "anomaly_date", nullable = false)
    private LocalDate anomalyDate;

    @Column(name = "observed_amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal observedAmount;

    @Column(name = "baseline_amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal baselineAmount;

    @Column(name = "increase_amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal increaseAmount;

    @Column(name = "increase_percent", precision = 12, scale = 2)
    private BigDecimal increasePercent;

    @Column(name = "baseline_days", nullable = false)
    private int baselineDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Severity severity;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AnomalyStatus status;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CostAnomaly() {
    }

    public static CostAnomaly open(AnomalyFinding finding, Instant now) {
        CostAnomaly anomaly = new CostAnomaly();
        anomaly.dimension = finding.dimension();
        anomaly.dimensionValue = finding.dimensionValue();
        anomaly.anomalyDate = finding.date();
        anomaly.status = AnomalyStatus.OPEN;
        anomaly.detectedAt = now;
        anomaly.refresh(finding, now);
        return anomaly;
    }

    /** Updates the supporting figures after a re-run; the review status is kept. */
    public void refresh(AnomalyFinding finding, Instant now) {
        this.observedAmount = finding.observed();
        this.baselineAmount = finding.baseline();
        this.increaseAmount = finding.increase();
        this.increasePercent = finding.increasePercent();
        this.baselineDays = finding.baselineDays();
        this.severity = finding.severity();
        this.reason = finding.reason();
        this.updatedAt = now;
    }

    public void review(AnomalyStatus newStatus, String note, Instant now) {
        this.status = newStatus;
        this.reviewNote = note == null || note.isBlank() ? null : note.strip();
        this.reviewedAt = newStatus == AnomalyStatus.OPEN ? null : now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public CostDimension getDimension() {
        return dimension;
    }

    public String getDimensionValue() {
        return dimensionValue;
    }

    public LocalDate getAnomalyDate() {
        return anomalyDate;
    }

    public BigDecimal getObservedAmount() {
        return observedAmount;
    }

    public BigDecimal getBaselineAmount() {
        return baselineAmount;
    }

    public BigDecimal getIncreaseAmount() {
        return increaseAmount;
    }

    public BigDecimal getIncreasePercent() {
        return increasePercent;
    }

    public int getBaselineDays() {
        return baselineDays;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getReason() {
        return reason;
    }

    public AnomalyStatus getStatus() {
        return status;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
