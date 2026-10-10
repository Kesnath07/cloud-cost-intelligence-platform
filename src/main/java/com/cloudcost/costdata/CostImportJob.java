package com.cloudcost.costdata;

import java.time.Instant;
import java.time.LocalDate;

import com.cloudcost.common.DateRange;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Audit record for one ingestion attempt: a CSV upload, the demonstration dataset or a
 * Cost Explorer synchronization. Failed attempts are recorded as well so that operators can
 * see why data is missing.
 */
@Entity
@Table(name = "cost_import_job")
public class CostImportJob {

    static final int MAX_ERROR_LENGTH = 8000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CostSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 16)
    private ImportDataType dataType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ImportStatus status;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_checksum", length = 64)
    private String fileChecksum;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "records_received", nullable = false)
    private int recordsReceived;

    @Column(name = "records_inserted", nullable = false)
    private int recordsInserted;

    @Column(name = "records_skipped", nullable = false)
    private int recordsSkipped;

    @Column(name = "records_replaced", nullable = false)
    private int recordsReplaced;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "removed_at")
    private Instant removedAt;

    protected CostImportJob() {
    }

    private CostImportJob(CostSource source, ImportDataType dataType, String fileName, String fileChecksum,
            Instant startedAt) {
        this.source = source;
        this.dataType = dataType;
        this.status = ImportStatus.RUNNING;
        this.fileName = fileName;
        this.fileChecksum = fileChecksum;
        this.startedAt = startedAt;
    }

    public static CostImportJob startFileImport(CostSource source, ImportDataType dataType, String fileName,
            String fileChecksum, Instant now) {
        return new CostImportJob(source, dataType, fileName, fileChecksum, now);
    }

    public static CostImportJob startSync(DateRange period, Instant now) {
        CostImportJob job = new CostImportJob(CostSource.AWS_COST_EXPLORER, ImportDataType.COST, null, null, now);
        job.setPeriod(period);
        return job;
    }

    public void setPeriod(DateRange period) {
        this.periodStart = period == null ? null : period.start();
        this.periodEnd = period == null ? null : period.end();
    }

    public void complete(int received, int inserted, int skipped, int replaced, Instant now) {
        this.status = ImportStatus.COMPLETED;
        this.recordsReceived = received;
        this.recordsInserted = inserted;
        this.recordsSkipped = skipped;
        this.recordsReplaced = replaced;
        this.completedAt = now;
    }

    public void fail(int received, String message, Instant now) {
        this.status = ImportStatus.FAILED;
        this.recordsReceived = received;
        this.errorMessage = truncate(message);
        this.completedAt = now;
    }

    public void markRemoved(Instant now) {
        this.status = ImportStatus.REMOVED;
        this.removedAt = now;
    }

    public boolean isRemovable() {
        return status == ImportStatus.COMPLETED && source != CostSource.AWS_COST_EXPLORER;
    }

    private static String truncate(String message) {
        if (message == null || message.length() <= MAX_ERROR_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_LENGTH - 3) + "...";
    }

    public Long getId() {
        return id;
    }

    public CostSource getSource() {
        return source;
    }

    public ImportDataType getDataType() {
        return dataType;
    }

    public ImportStatus getStatus() {
        return status;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFileChecksum() {
        return fileChecksum;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public int getRecordsReceived() {
        return recordsReceived;
    }

    public int getRecordsInserted() {
        return recordsInserted;
    }

    public int getRecordsSkipped() {
        return recordsSkipped;
    }

    public int getRecordsReplaced() {
        return recordsReplaced;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getRemovedAt() {
        return removedAt;
    }
}
