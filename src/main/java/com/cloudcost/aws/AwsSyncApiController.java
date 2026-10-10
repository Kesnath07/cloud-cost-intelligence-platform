package com.cloudcost.aws;

import java.time.LocalDate;

import com.cloudcost.ingestion.ImportJobResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/aws")
class AwsSyncApiController {

    private final CostExplorerSyncService syncService;
    private final AwsIntegrationStatus status;

    AwsSyncApiController(CostExplorerSyncService syncService, AwsIntegrationStatus status) {
        this.syncService = syncService;
        this.status = status;
    }

    record SyncRequest(@NotNull LocalDate start, @NotNull LocalDate end) {
    }

    record StatusResponse(boolean enabled, String region, String costMetric, String environmentTagKey,
            boolean scheduledSyncEnabled, ImportJobResponse lastSync) {
    }

    @GetMapping("/status")
    StatusResponse status() {
        AwsIntegrationStatus.View view = status.view();
        return new StatusResponse(view.enabled(), view.region(), view.costMetric(), view.environmentTagKey(),
                view.scheduledSyncEnabled(), view.lastSync() == null ? null : ImportJobResponse.from(view.lastSync()));
    }

    @PostMapping("/sync")
    @ResponseStatus(HttpStatus.CREATED)
    ImportJobResponse sync(@Valid @RequestBody SyncRequest request) {
        return ImportJobResponse.from(syncService.sync(request.start(), request.end()));
    }
}
