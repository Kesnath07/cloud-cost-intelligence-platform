package com.cloudcost.costdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import com.cloudcost.support.PostgresIntegrationTest;

import com.cloudcost.support.DatabaseCleaner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@PostgresIntegrationTest
class SchemaMigrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CostImportJobRepository jobs;

    @BeforeEach
    void cleanDatabase() {
        DatabaseCleaner.clean(jdbc);
    }

    @Test
    void migrationsCreateCostDataTables() {
        Integer tables = jdbc.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema = 'public'
                  and table_name in ('cost_import_job', 'cost_record', 'resource_utilization', 'cost_budget',
                                     'cost_anomaly', 'optimization_recommendation')
                """, Integer.class);
        assertThat(tables).isEqualTo(6);
    }

    @Test
    void completedImportsOfTheSameFileAreRejectedByTheDatabase() {
        CostImportJob first = CostImportJob.startFileImport(CostSource.CSV_UPLOAD, ImportDataType.COST,
                "a.csv", "f".repeat(64), Instant.now());
        first.complete(1, 1, 0, 0, Instant.now());
        jobs.saveAndFlush(first);

        CostImportJob second = CostImportJob.startFileImport(CostSource.CSV_UPLOAD, ImportDataType.COST,
                "b.csv", "f".repeat(64), Instant.now());
        second.complete(1, 1, 0, 0, Instant.now());

        assertThatThrownBy(() -> jobs.saveAndFlush(second)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseConstraintsRejectInvalidValues() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into cost_budget (name, limit_amount, currency, period, warning_threshold_percent, created_at,
                                         updated_at)
                values ('x', 0, 'USD', 'MONTHLY', 80, now(), now())
                """)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                insert into resource_utilization (import_job_id, resource_id, service_name, period_start, period_end,
                                                  avg_cpu_percent, max_cpu_percent, ingested_at)
                values (1, 'i-1', 'EC2', '2026-09-01', '2026-09-30', 50, 20, now())
                """)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void costRecordsAreUniquePerNaturalKey() {
        CostImportJob job = CostImportJob.startFileImport(CostSource.CSV_UPLOAD, ImportDataType.COST, "a.csv",
                "a".repeat(64), Instant.now());
        job.complete(1, 1, 0, 0, Instant.now());
        long jobId = jobs.saveAndFlush(job).getId();
        String insert = """
                insert into cost_record (import_job_id, source, usage_date, service_name, charge_type, amount,
                                         currency, ingested_at)
                values (?, 'CSV_UPLOAD', '2026-09-01', 'EC2', 'USAGE', 1.00, 'USD', now())
                """;
        jdbc.update(insert, jobId);

        assertThatThrownBy(() -> jdbc.update(insert, jobId)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
