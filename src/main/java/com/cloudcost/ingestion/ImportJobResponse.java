package com.cloudcost.ingestion;

import java.time.Instant;
import java.time.LocalDate;

import com.cloudcost.costdata.CostImportJob;
import com.cloudcost.costdata.CostSource;
import com.cloudcost.costdata.ImportDataType;
import com.cloudcost.costdata.ImportStatus;

/** API representation of an import job. */
public record ImportJobResponse(
        long id,
        CostSource source,
        ImportDataType dataType,
        ImportStatus status,
        String fileName,
        LocalDate periodStart,
        LocalDate periodEnd,
        int recordsReceived,
        int recordsInserted,
        int recordsSkipped,
        int recordsReplaced,
        String errorMessage,
        Instant startedAt,
        Instant completedAt,
        Instant removedAt) {

    public static ImportJobResponse from(CostImportJob job) {
        return new ImportJobResponse(job.getId(), job.getSource(), job.getDataType(), job.getStatus(),
                job.getFileName(), job.getPeriodStart(), job.getPeriodEnd(), job.getRecordsReceived(),
                job.getRecordsInserted(), job.getRecordsSkipped(), job.getRecordsReplaced(), job.getErrorMessage(),
                job.getStartedAt(), job.getCompletedAt(), job.getRemovedAt());
    }
}
