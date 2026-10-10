package com.cloudcost.ingestion;

import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.cloudcost.common.DateRange;
import com.cloudcost.costdata.CostSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Bulk writes for cost and utilization rows. Uses PostgreSQL {@code ON CONFLICT DO NOTHING}
 * so that re-importing overlapping data is idempotent and concurrency-safe: rows whose
 * natural key already exists are skipped and counted rather than duplicated.
 */
@Repository
public class CostRecordWriter {

    private static final int BATCH_SIZE = 1000;

    private static final String INSERT_COST = """
            INSERT INTO cost_record (import_job_id, source, usage_date, account_id, service_name, region,
                                     environment, charge_type, amount, currency, estimated, ingested_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT ON CONSTRAINT uk_cost_record_natural_key DO NOTHING
            """;

    private static final String INSERT_UTILIZATION = """
            INSERT INTO resource_utilization (import_job_id, resource_id, resource_type, service_name, environment,
                                              region, period_start, period_end, avg_cpu_percent, max_cpu_percent,
                                              period_cost, currency, ingested_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT ON CONSTRAINT uk_resource_utilization_period DO NOTHING
            """;

    private final JdbcTemplate jdbc;

    public CostRecordWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Inserts records that do not exist yet and returns how many were inserted. */
    public int insertCostRecords(long importJobId, CostSource source, List<NormalizedCostRecord> records,
            Instant ingestedAt) {
        OffsetDateTime timestamp = OffsetDateTime.ofInstant(ingestedAt, ZoneOffset.UTC);
        int[][] counts = jdbc.batchUpdate(INSERT_COST, records, BATCH_SIZE,
                (PreparedStatement ps, NormalizedCostRecord r) -> {
                    ps.setLong(1, importJobId);
                    ps.setString(2, source.name());
                    ps.setObject(3, r.usageDate());
                    ps.setString(4, r.accountId());
                    ps.setString(5, r.serviceName());
                    ps.setString(6, r.region());
                    ps.setString(7, r.environment());
                    ps.setString(8, r.chargeType().name());
                    ps.setBigDecimal(9, r.amount());
                    ps.setString(10, r.currency());
                    ps.setBoolean(11, r.estimated());
                    ps.setObject(12, timestamp);
                });
        return sumInserted(counts);
    }

    public int insertUtilization(long importJobId, List<NormalizedUtilization> records, Instant ingestedAt) {
        OffsetDateTime timestamp = OffsetDateTime.ofInstant(ingestedAt, ZoneOffset.UTC);
        int[][] counts = jdbc.batchUpdate(INSERT_UTILIZATION, records, BATCH_SIZE,
                (PreparedStatement ps, NormalizedUtilization r) -> {
                    ps.setLong(1, importJobId);
                    ps.setString(2, r.resourceId());
                    ps.setString(3, r.resourceType());
                    ps.setString(4, r.serviceName());
                    ps.setString(5, r.environment());
                    ps.setString(6, r.region());
                    ps.setObject(7, r.periodStart());
                    ps.setObject(8, r.periodEnd());
                    ps.setBigDecimal(9, r.avgCpuPercent());
                    ps.setBigDecimal(10, r.maxCpuPercent());
                    ps.setBigDecimal(11, r.periodCost());
                    ps.setString(12, r.currency());
                    ps.setObject(13, timestamp);
                });
        return sumInserted(counts);
    }

    /** Deletes the records of one source within a date range; used before a Cost Explorer refresh. */
    public int deleteCostRecords(CostSource source, DateRange range) {
        return jdbc.update("DELETE FROM cost_record WHERE source = ? AND usage_date BETWEEN ? AND ?",
                source.name(), range.start(), range.end());
    }

    public int deleteByImportJob(long importJobId) {
        return jdbc.update("DELETE FROM cost_record WHERE import_job_id = ?", importJobId)
                + jdbc.update("DELETE FROM resource_utilization WHERE import_job_id = ?", importJobId);
    }

    private static int sumInserted(int[][] counts) {
        int inserted = 0;
        for (int[] batch : counts) {
            for (int count : batch) {
                if (count > 0) {
                    inserted += count;
                }
            }
        }
        return inserted;
    }
}
