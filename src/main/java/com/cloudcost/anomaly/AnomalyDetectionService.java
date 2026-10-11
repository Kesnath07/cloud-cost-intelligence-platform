package com.cloudcost.anomaly;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.cloudcost.analysis.CostAnalysisService;
import com.cloudcost.analysis.CostDimension;
import com.cloudcost.analysis.CostFilter;
import com.cloudcost.common.DateRange;
import com.cloudcost.common.NotFoundException;
import com.cloudcost.config.PlatformProperties;
import com.cloudcost.costdata.CostDataChangedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the {@link AnomalyDetector} for every service and every environment, and reconciles the
 * findings with stored anomalies: new findings are stored as OPEN, existing ones get refreshed
 * figures while keeping their review status, and OPEN anomalies that the current data no longer
 * supports are deleted. Reviewed anomalies are kept as history.
 */
@Service
public class AnomalyDetectionService {

    /** Default evaluation window when detection is started without dates. */
    public static final int DEFAULT_EVALUATION_DAYS = 90;
    private static final Logger log = LoggerFactory.getLogger(AnomalyDetectionService.class);

    private final CostAnalysisService analysis;
    private final CostAnomalyRepository anomalies;
    private final AnomalyProperties properties;
    private final AnomalyDetector detector;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AnomalyDetectionService(CostAnalysisService analysis, CostAnomalyRepository anomalies,
            AnomalyProperties properties, PlatformProperties platformProperties, ApplicationEventPublisher events,
            Clock clock) {
        this.analysis = analysis;
        this.anomalies = anomalies;
        this.properties = properties;
        this.detector = new AnomalyDetector(properties, platformProperties.reportingCurrency());
        this.events = events;
        this.clock = clock;
    }

    public record DetectionResult(DateRange evaluated, int findings, int created, int updated, int removed) {
    }

    public DateRange defaultEvaluationRange() {
        return DateRange.endingOn(analysis.latestDataDateOrToday(), DEFAULT_EVALUATION_DAYS);
    }

    @Transactional
    public DetectionResult detect(DateRange evaluation) {
        List<AnomalyFinding> findings = findAll(evaluation);
        Instant now = Instant.now(clock);

        Map<String, CostAnomaly> existing = new HashMap<>();
        for (CostAnomaly anomaly : anomalies.findAllByAnomalyDateBetween(evaluation.start(), evaluation.end())) {
            existing.put(key(anomaly.getDimension(), anomaly.getDimensionValue(), anomaly.getAnomalyDate()), anomaly);
        }
        List<AnomalyFinding> created = new ArrayList<>();
        int updated = 0;
        for (AnomalyFinding finding : findings) {
            CostAnomaly anomaly = existing.remove(key(finding.dimension(), finding.dimensionValue(), finding.date()));
            if (anomaly == null) {
                anomalies.save(CostAnomaly.open(finding, now));
                created.add(finding);
            } else {
                anomaly.refresh(finding, now);
                updated++;
            }
        }
        List<CostAnomaly> unsupported = existing.values().stream()
                .filter(anomaly -> anomaly.getStatus() == AnomalyStatus.OPEN)
                .toList();
        anomalies.deleteAll(unsupported);

        DetectionResult result = new DetectionResult(evaluation, findings.size(), created.size(), updated,
                unsupported.size());
        log.info("Anomaly detection for {}: {}", evaluation, result);
        if (!created.isEmpty()) {
            events.publishEvent(new NewAnomaliesEvent(created));
        }
        return result;
    }

    /**
     * Re-evaluates anomalies after cost data changed. Days after the changed range are included
     * because their baselines contain the changed days.
     */
    @Order(1)
    @EventListener
    public void onCostDataChanged(CostDataChangedEvent event) {
        DateRange changed = event.affectedRange();
        LocalDate latest = analysis.coverage().lastDate();
        LocalDate end = changed.end().plusDays(properties.baselineDays());
        if (latest == null || latest.isBefore(changed.end())) {
            end = changed.end();
        } else if (end.isAfter(latest)) {
            end = latest;
        }
        detect(DateRange.of(changed.start(), end));
    }

    @Transactional(readOnly = true)
    public Page<CostAnomaly> list(AnomalyStatus status, Pageable pageable) {
        return status == null ? anomalies.findAll(pageable) : anomalies.findAllByStatus(status, pageable);
    }

    @Transactional(readOnly = true)
    public CostAnomaly get(long id) {
        return anomalies.findById(id).orElseThrow(() -> NotFoundException.of("Anomaly", id));
    }

    @Transactional(readOnly = true)
    public long countOpen() {
        return anomalies.countByStatus(AnomalyStatus.OPEN);
    }

    @Transactional(readOnly = true)
    public List<CostAnomaly> findInRange(DateRange range) {
        return anomalies.findAllByAnomalyDateBetweenOrderByAnomalyDateDescIncreaseAmountDesc(range.start(), range.end());
    }

    @Transactional
    public CostAnomaly review(long id, AnomalyStatus status, String note) {
        CostAnomaly anomaly = get(id);
        anomaly.review(status, note, Instant.now(clock));
        return anomaly;
    }

    private List<AnomalyFinding> findAll(DateRange evaluation) {
        DateRange seriesRange = DateRange.of(evaluation.start().minusDays(properties.baselineDays()), evaluation.end());
        List<AnomalyFinding> findings = new ArrayList<>();
        for (CostDimension dimension : CostDimension.values()) {
            Map<String, TreeMap<LocalDate, BigDecimal>> series =
                    analysis.dailySeriesBy(dimension, seriesRange, CostFilter.NONE);
            series.forEach((value, daily) -> findings.addAll(detector.detect(dimension, value, daily, evaluation)));
        }
        return findings;
    }

    private static String key(CostDimension dimension, String value, LocalDate date) {
        return dimension + "|" + value + "|" + date;
    }
}
