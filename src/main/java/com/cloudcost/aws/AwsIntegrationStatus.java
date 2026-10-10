package com.cloudcost.aws;

import java.time.Clock;
import java.time.LocalDate;

import com.cloudcost.costdata.CostImportJob;
import com.cloudcost.costdata.CostImportJobRepository;
import com.cloudcost.costdata.CostSource;

import org.springframework.stereotype.Component;

/** Read-only integration status, exposed to templates as {@code @awsIntegrationStatus}. */
@Component("awsIntegrationStatus")
public class AwsIntegrationStatus {

    private final CostExplorerSyncService syncService;
    private final AwsProperties properties;
    private final CostImportJobRepository jobs;
    private final Clock clock;

    public AwsIntegrationStatus(CostExplorerSyncService syncService, AwsProperties properties,
            CostImportJobRepository jobs, Clock clock) {
        this.syncService = syncService;
        this.properties = properties;
        this.jobs = jobs;
        this.clock = clock;
    }

    public record View(boolean enabled, String region, String costMetric, String environmentTagKey,
            boolean scheduledSyncEnabled, String scheduledSyncCron, int scheduledSyncLookbackDays, int maxSyncDays,
            LocalDate suggestedStart, LocalDate suggestedEnd, CostImportJob lastSync) {
    }

    public View view() {
        LocalDate yesterday = LocalDate.now(clock).minusDays(1);
        CostImportJob lastSync = jobs.findFirstBySourceOrderByStartedAtDescIdDesc(CostSource.AWS_COST_EXPLORER)
                .orElse(null);
        return new View(syncService.isEnabled(), properties.region(), properties.costMetric(),
                properties.environmentTagKey(), properties.scheduledSyncEnabled(), properties.scheduledSyncCron(),
                properties.scheduledSyncLookbackDays(), properties.maxSyncDays(), yesterday.minusDays(13), yesterday,
                lastSync);
    }
}
