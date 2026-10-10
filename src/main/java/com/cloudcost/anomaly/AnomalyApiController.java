package com.cloudcost.anomaly;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.cloudcost.analysis.CostDimension;
import com.cloudcost.common.DateRange;
import com.cloudcost.common.Severity;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/anomalies")
class AnomalyApiController {

    static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("anomalyDate"), Sort.Order.desc("increaseAmount"));

    private final AnomalyDetectionService detection;

    AnomalyApiController(AnomalyDetectionService detection) {
        this.detection = detection;
    }

    record AnomalyResponse(long id, CostDimension dimension, String dimensionValue, LocalDate date,
            BigDecimal observedAmount, BigDecimal baselineAmount, BigDecimal increaseAmount,
            BigDecimal increasePercent, int baselineDays, Severity severity, String reason, AnomalyStatus status,
            String reviewNote, Instant reviewedAt, Instant detectedAt) {

        static AnomalyResponse from(CostAnomaly a) {
            return new AnomalyResponse(a.getId(), a.getDimension(), a.getDimensionValue(), a.getAnomalyDate(),
                    a.getObservedAmount(), a.getBaselineAmount(), a.getIncreaseAmount(), a.getIncreasePercent(),
                    a.getBaselineDays(), a.getSeverity(), a.getReason(), a.getStatus(), a.getReviewNote(),
                    a.getReviewedAt(), a.getDetectedAt());
        }
    }

    @GetMapping
    Page<AnomalyResponse> list(@RequestParam(required = false) AnomalyStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        PageRequest request = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), NEWEST_FIRST);
        return detection.list(status, request).map(AnomalyResponse::from);
    }

    @GetMapping("/{id}")
    AnomalyResponse get(@PathVariable long id) {
        return AnomalyResponse.from(detection.get(id));
    }

    @PostMapping("/detect")
    AnomalyDetectionService.DetectionResult detect(
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate end) {
        DateRange range = start == null && end == null ? detection.defaultEvaluationRange()
                : DateRange.ofRequest(start, end);
        return detection.detect(range);
    }

    @PatchMapping("/{id}")
    AnomalyResponse review(@PathVariable long id, @Valid @RequestBody AnomalyReviewRequest request) {
        return AnomalyResponse.from(detection.review(id, request.status(), request.note()));
    }
}
