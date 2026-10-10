package com.cloudcost.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.cloudcost.common.InvalidRequestException;
import com.cloudcost.costdata.CostImportJobRepository;
import com.cloudcost.costdata.CostRecordRepository;
import com.cloudcost.costdata.CostSource;
import com.cloudcost.costdata.ImportStatus;
import com.cloudcost.costdata.ResourceUtilizationRepository;
import com.cloudcost.support.PostgresIntegrationTest;

import com.cloudcost.support.DatabaseCleaner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@PostgresIntegrationTest
class CostIngestionServiceIntegrationTest {

    private static final String FILE = """
            usage_date,service,amount,currency,environment
            2026-09-01,EC2,10.00,USD,production
            2026-09-02,EC2,12.50,USD,production
            2026-09-02,S3,1.25,USD,
            """;

    @Autowired
    private CostIngestionService ingestion;

    @Autowired
    private CostRecordRepository records;

    @Autowired
    private ResourceUtilizationRepository utilization;

    @Autowired
    private CostImportJobRepository jobs;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        DatabaseCleaner.clean(jdbc);
    }

    private static byte[] bytes(String csv) {
        return csv.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void importsValidFileAndRecordsTheJob() {
        ImportOutcome outcome = ingestion.importCostFile(CostSource.CSV_UPLOAD, "costs.csv", bytes(FILE));

        assertThat(outcome.succeeded()).isTrue();
        assertThat(records.count()).isEqualTo(3);
        assertThat(outcome.job().getRecordsInserted()).isEqualTo(3);
        assertThat(outcome.job().getPeriodStart()).hasToString("2026-09-01");
        assertThat(outcome.job().getPeriodEnd()).hasToString("2026-09-02");
        assertThat(outcome.job().getCompletedAt()).isNotNull();
    }

    @Test
    void rejectsIdenticalFileContentAndRecordsTheAttempt() {
        ingestion.importCostFile(CostSource.CSV_UPLOAD, "costs.csv", bytes(FILE));

        assertThatThrownBy(() -> ingestion.importCostFile(CostSource.CSV_UPLOAD, "copy.csv", bytes(FILE)))
                .isInstanceOf(DuplicateImportException.class)
                .hasMessageContaining("already imported");
        assertThat(records.count()).isEqualTo(3);
        assertThat(jobs.findAll()).extracting(job -> job.getStatus())
                .containsExactlyInAnyOrder(ImportStatus.COMPLETED, ImportStatus.FAILED);
    }

    @Test
    void skipsRowsThatAlreadyExistWhenFilesOverlap() {
        ingestion.importCostFile(CostSource.CSV_UPLOAD, "costs.csv", bytes(FILE));

        ImportOutcome overlap = ingestion.importCostFile(CostSource.CSV_UPLOAD, "overlap.csv", bytes("""
                usage_date,service,amount,currency,environment
                2026-09-02,EC2,12.50,USD,production
                2026-09-03,EC2,11.00,USD,production
                """));

        assertThat(overlap.succeeded()).isTrue();
        assertThat(overlap.job().getRecordsInserted()).isEqualTo(1);
        assertThat(overlap.job().getRecordsSkipped()).isEqualTo(1);
        assertThat(records.count()).isEqualTo(4);
    }

    @Test
    void invalidFilesAreRecordedAsFailedWithoutStoringRecords() {
        ImportOutcome outcome = ingestion.importCostFile(CostSource.CSV_UPLOAD, "../../bad.csv", bytes("""
                usage_date,service,amount,currency
                2026-09-01,EC2,1.00,USD
                not-a-date,EC2,1.00,USD
                """));

        assertThat(outcome.succeeded()).isFalse();
        assertThat(outcome.job().getStatus()).isEqualTo(ImportStatus.FAILED);
        assertThat(outcome.job().getFileName()).isEqualTo("bad.csv");
        assertThat(outcome.job().getErrorMessage()).contains("Row 3, column 'usage_date'");
        assertThat(records.count()).isZero();
    }

    @Test
    void removingAnImportDeletesItsRecordsAndAllowsReimport() {
        ImportOutcome first = ingestion.importCostFile(CostSource.CSV_UPLOAD, "costs.csv", bytes(FILE));

        ingestion.removeImport(first.job().getId());

        assertThat(records.count()).isZero();
        assertThat(jobs.findById(first.job().getId())).get()
                .satisfies(job -> assertThat(job.getStatus()).isEqualTo(ImportStatus.REMOVED));
        assertThat(ingestion.importCostFile(CostSource.CSV_UPLOAD, "costs.csv", bytes(FILE)).succeeded()).isTrue();
    }

    @Test
    void failedImportsCannotBeRemoved() {
        ImportOutcome failed = ingestion.importCostFile(CostSource.CSV_UPLOAD, "bad.csv", bytes("x\n1\n"));

        assertThatThrownBy(() -> ingestion.removeImport(failed.job().getId()))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void demoDatasetLoadsOnceAndCanBeRemoved() {
        List<ImportOutcome> outcomes = ingestion.loadDemoDataset();

        assertThat(outcomes).allMatch(ImportOutcome::succeeded);
        assertThat(records.existsBySource(CostSource.DEMO_SAMPLE)).isTrue();
        assertThat(records.count()).isEqualTo(3995);
        assertThat(utilization.count()).isEqualTo(6);
        assertThatThrownBy(ingestion::loadDemoDataset).isInstanceOf(DuplicateImportException.class);

        assertThat(ingestion.removeDemoData()).isEqualTo(2);
        assertThat(records.count()).isZero();
        assertThat(utilization.count()).isZero();
    }
}
