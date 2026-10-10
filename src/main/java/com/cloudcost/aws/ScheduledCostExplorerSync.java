package com.cloudcost.aws;

import java.time.Clock;
import java.time.LocalDate;

import com.cloudcost.common.ConflictException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Optional daily refresh of the most recent days. Disabled unless both
 * {@code costplatform.aws.enabled} and {@code costplatform.aws.scheduled-sync-enabled} are true.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "costplatform.aws", name = {"enabled", "scheduled-sync-enabled"}, havingValue = "true")
class ScheduledCostExplorerSync {

    private static final Logger log = LoggerFactory.getLogger(ScheduledCostExplorerSync.class);

    private final CostExplorerSyncService syncService;
    private final AwsProperties properties;
    private final Clock clock;

    ScheduledCostExplorerSync(CostExplorerSyncService syncService, AwsProperties properties, Clock clock) {
        this.syncService = syncService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${costplatform.aws.scheduled-sync-cron:0 30 6 * * *}", zone = "UTC")
    void refreshRecentDays() {
        LocalDate yesterday = LocalDate.now(clock).minusDays(1);
        try {
            syncService.sync(yesterday.minusDays(properties.scheduledSyncLookbackDays() - 1L), yesterday);
        } catch (ConflictException ex) {
            log.info("Skipping scheduled Cost Explorer synchronization: {}", ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("Scheduled Cost Explorer synchronization failed: {}", ex.getMessage());
        }
    }
}
