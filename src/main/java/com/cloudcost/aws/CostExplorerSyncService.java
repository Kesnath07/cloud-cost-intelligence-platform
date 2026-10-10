package com.cloudcost.aws;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import com.cloudcost.common.ConflictException;
import com.cloudcost.common.DateRange;
import com.cloudcost.common.InvalidRequestException;
import com.cloudcost.config.PlatformProperties;
import com.cloudcost.costdata.CostDataChangedEvent;
import com.cloudcost.costdata.CostImportJob;
import com.cloudcost.costdata.CostImportJobRepository;
import com.cloudcost.costdata.CostSource;
import com.cloudcost.costdata.ImportStatus;
import com.cloudcost.ingestion.CostRecordWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;

/**
 * Synchronizes a date range from Cost Explorer. Cost Explorer revises recent (estimated)
 * figures, so a synchronization replaces all previously synchronized records in the range
 * within one transaction rather than merging with them.
 */
@Service
public class CostExplorerSyncService {

    private static final Logger log = LoggerFactory.getLogger(CostExplorerSyncService.class);

    private final ObjectProvider<CostExplorerClient> client;
    private final AwsProperties properties;
    private final PlatformProperties platformProperties;
    private final CostImportJobRepository jobs;
    private final CostRecordWriter writer;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public CostExplorerSyncService(ObjectProvider<CostExplorerClient> client, AwsProperties properties,
            PlatformProperties platformProperties, CostImportJobRepository jobs, CostRecordWriter writer,
            TransactionTemplate transactions, ApplicationEventPublisher events, Clock clock) {
        this.client = client;
        this.properties = properties;
        this.platformProperties = platformProperties;
        this.jobs = jobs;
        this.writer = writer;
        this.transactions = transactions;
        this.events = events;
        this.clock = clock;
    }

    public boolean isEnabled() {
        return properties.enabled() && client.getIfAvailable() != null;
    }

    public CostImportJob sync(LocalDate start, LocalDate end) {
        CostExplorerClient costExplorer = client.getIfAvailable();
        if (!properties.enabled() || costExplorer == null) {
            throw new AwsIntegrationException("AWS Cost Explorer integration is disabled. Set "
                    + "COSTPLATFORM_AWS_ENABLED=true and provide credentials through the default AWS credential "
                    + "provider chain, or import cost data from CSV instead.");
        }
        DateRange range = validate(start, end);
        recoverInterruptedJobs();

        CostImportJob job;
        try {
            job = jobs.saveAndFlush(CostImportJob.startSync(range, Instant.now(clock)));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("A Cost Explorer synchronization is already running. Wait for it to finish.");
        }

        CostExplorerFetcher.FetchResult result;
        try {
            result = new CostExplorerFetcher(costExplorer, properties, platformProperties).fetch(range);
        } catch (SdkException ex) {
            AwsIntegrationException translated = AwsErrorTranslator.translate(ex, properties.environmentTagKey());
            log.warn("Cost Explorer synchronization {} failed: {}", job.getId(), translated.getMessage(), ex);
            markFailed(job, translated.getMessage());
            throw translated;
        } catch (RuntimeException ex) {
            log.warn("Cost Explorer synchronization {} failed", job.getId(), ex);
            markFailed(job, ex instanceof AwsIntegrationException ? ex.getMessage()
                    : "Unexpected error while reading Cost Explorer data; see application logs.");
            throw ex;
        }

        CostImportJob completed = transactions.execute(status -> {
            int replaced = writer.deleteCostRecords(CostSource.AWS_COST_EXPLORER, range);
            int inserted = writer.insertCostRecords(job.getId(), CostSource.AWS_COST_EXPLORER, result.records(),
                    Instant.now(clock));
            job.complete(result.records().size(), inserted, result.records().size() - inserted, replaced,
                    Instant.now(clock));
            return jobs.save(job);
        });
        log.info("Cost Explorer synchronization {} for {} completed: {} records from {} page(s), {} replaced",
                job.getId(), range, completed.getRecordsInserted(), result.pages(), completed.getRecordsReplaced());
        events.publishEvent(new CostDataChangedEvent(range));
        return completed;
    }

    private DateRange validate(LocalDate start, LocalDate end) {
        DateRange range = DateRange.ofRequest(start, end);
        LocalDate today = LocalDate.now(clock);
        if (range.end().isAfter(today)) {
            throw new InvalidRequestException("The end date cannot be in the future (today is " + today + " UTC)");
        }
        if (range.days() > properties.maxSyncDays()) {
            throw new InvalidRequestException("A synchronization can cover at most " + properties.maxSyncDays()
                    + " days; split the range into smaller periods.");
        }
        return range;
    }

    private void markFailed(CostImportJob job, String message) {
        job.fail(0, message, Instant.now(clock));
        jobs.save(job);
    }

    /** Marks synchronizations left RUNNING by a stopped instance as failed so a new one can start. */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedJobs() {
        Instant cutoff = Instant.now(clock).minus(properties.staleJobTimeout());
        for (CostImportJob job : jobs.findAllBySourceAndStatus(CostSource.AWS_COST_EXPLORER, ImportStatus.RUNNING)) {
            if (job.getStartedAt().isBefore(cutoff)) {
                markFailed(job, "Interrupted: the synchronization did not finish within "
                        + properties.staleJobTimeout() + ".");
                log.warn("Marked interrupted Cost Explorer synchronization {} as failed", job.getId());
            }
        }
    }
}
