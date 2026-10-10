package com.cloudcost.ingestion;

import java.util.List;

import com.cloudcost.costdata.CostImportJob;
import com.cloudcost.costdata.ImportStatus;

/** Result of a file import: the recorded job plus any validation errors. */
public record ImportOutcome(CostImportJob job, List<RowError> errors, boolean errorsTruncated) {

    public ImportOutcome {
        errors = List.copyOf(errors);
    }

    public boolean succeeded() {
        return job.getStatus() == ImportStatus.COMPLETED;
    }

    public String summary() {
        if (!succeeded()) {
            return "Import of " + job.getFileName() + " failed: " + errors.size()
                    + (errorsTruncated ? "+" : "") + " validation error(s). No records were imported.";
        }
        String text = "Imported " + job.getRecordsInserted() + " of " + job.getRecordsReceived()
                + " record(s) from " + job.getFileName();
        if (job.getRecordsSkipped() > 0) {
            text += "; " + job.getRecordsSkipped() + " already existed and were skipped";
        }
        if (job.getPeriodStart() != null) {
            text += ". Data covers " + job.getPeriodStart() + " to " + job.getPeriodEnd();
        }
        return text + ".";
    }
}
