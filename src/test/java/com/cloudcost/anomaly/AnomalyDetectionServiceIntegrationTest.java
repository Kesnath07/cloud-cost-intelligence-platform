package com.cloudcost.anomaly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.util.List;

import com.cloudcost.analysis.CostDimension;
import com.cloudcost.common.DateRange;
import com.cloudcost.common.Severity;
import com.cloudcost.ingestion.CostIngestionService;
import com.cloudcost.ingestion.ImportOutcome;
import com.cloudcost.support.CostDataFixture;
import com.cloudcost.support.DatabaseCleaner;
import com.cloudcost.support.PostgresIntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@PostgresIntegrationTest
@RecordApplicationEvents
class AnomalyDetectionServiceIntegrationTest {

    @Autowired
    private AnomalyDetectionService detection;

    @Autowired
    private CostAnomalyRepository anomalies;

    @Autowired
    private CostIngestionService ingestion;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationEvents events;

    @BeforeEach
    void cleanDatabase() {
        DatabaseCleaner.clean(jdbc);
    }

    private static String steadyWithSpike(String spikeAmount) {
        StringBuilder rows = new StringBuilder();
        for (int day = 1; day <= 20; day++) {
            String amount = day == 18 ? spikeAmount : "20.00";
            rows.append("2026-09-%02d,EC2 - Other,%s,USD,production,USAGE%n".formatted(day, amount));
            rows.append("2026-09-%02d,S3,50.00,USD,staging,USAGE%n".formatted(day));
        }
        return rows.toString();
    }

    @Test
    void detectsAnomaliesAutomaticallyAfterAnImport() {
        CostDataFixture.importRows(ingestion, steadyWithSpike("180.00"));

        assertThat(anomalies.findAll())
                .extracting(CostAnomaly::getDimension, CostAnomaly::getDimensionValue, CostAnomaly::getAnomalyDate,
                        CostAnomaly::getSeverity, CostAnomaly::getStatus)
                .containsExactlyInAnyOrder(
                        tuple(CostDimension.SERVICE, "EC2 - Other", LocalDate.of(2026, 9, 18), Severity.MEDIUM,
                                AnomalyStatus.OPEN),
                        tuple(CostDimension.ENVIRONMENT, "production", LocalDate.of(2026, 9, 18), Severity.MEDIUM,
                                AnomalyStatus.OPEN));
    }

    @Test
    void announcesNewFindingsOnceAndNotWhenDetectionIsRerun() {
        CostDataFixture.importRows(ingestion, steadyWithSpike("180.00"));

        assertThat(events.stream(NewAnomaliesEvent.class)).singleElement()
                .satisfies(event -> assertThat(event.findings()).hasSize(2));

        detection.detect(DateRange.of(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 20)));

        assertThat(events.stream(NewAnomaliesEvent.class)).hasSize(1);
    }

    @Test
    void rerunningDetectionIsIdempotentAndKeepsReviews() {
        CostDataFixture.importRows(ingestion, steadyWithSpike("180.00"));
        CostAnomaly anomaly = anomalies.findAll().get(0);
        detection.review(anomaly.getId(), AnomalyStatus.DISMISSED, "Planned load test");

        AnomalyDetectionService.DetectionResult result =
                detection.detect(DateRange.of(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 20)));

        assertThat(result.created()).isZero();
        assertThat(result.updated()).isEqualTo(2);
        assertThat(anomalies.count()).isEqualTo(2);
        assertThat(anomalies.findById(anomaly.getId())).get().satisfies(reviewed -> {
            assertThat(reviewed.getStatus()).isEqualTo(AnomalyStatus.DISMISSED);
            assertThat(reviewed.getReviewNote()).isEqualTo("Planned load test");
            assertThat(reviewed.getReviewedAt()).isNotNull();
        });
    }

    @Test
    void openFindingsDisappearWhenTheirDataIsRemovedButReviewedOnesRemain() {
        ImportOutcome outcome = CostDataFixture.importRows(ingestion, steadyWithSpike("180.00"));
        List<CostAnomaly> found = anomalies.findAll();
        detection.review(found.get(0).getId(), AnomalyStatus.ACKNOWLEDGED, null);

        ingestion.removeImport(outcome.job().getId());

        assertThat(anomalies.findAll()).singleElement()
                .satisfies(remaining -> assertThat(remaining.getStatus()).isEqualTo(AnomalyStatus.ACKNOWLEDGED));
    }

    @Test
    void steadySpendingProducesNoFindings() {
        CostDataFixture.importRows(ingestion, steadyWithSpike("20.00"));

        assertThat(anomalies.count()).isZero();
    }

    @Test
    void demonstrationDatasetFlagsOnlyTheInjectedSpike() {
        ingestion.loadDemoDataset();

        assertThat(anomalies.findAll())
                .extracting(CostAnomaly::getDimensionValue, CostAnomaly::getAnomalyDate)
                .containsExactlyInAnyOrder(
                        tuple("EC2 - Other", LocalDate.of(2026, 9, 22)),
                        tuple("EC2 - Other", LocalDate.of(2026, 9, 23)),
                        tuple("production", LocalDate.of(2026, 9, 22)),
                        tuple("production", LocalDate.of(2026, 9, 23)));
    }
}
