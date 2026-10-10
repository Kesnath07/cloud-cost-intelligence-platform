package com.cloudcost.ingestion;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.cloudcost.common.DateRange;
import com.cloudcost.common.InvalidRequestException;
import com.cloudcost.common.NotFoundException;
import com.cloudcost.costdata.CostDataChangedEvent;
import com.cloudcost.costdata.CostImportJob;
import com.cloudcost.costdata.CostImportJobRepository;
import com.cloudcost.costdata.CostSource;
import com.cloudcost.costdata.ImportDataType;
import com.cloudcost.costdata.ImportStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * Imports cost and utilization files.
 *
 * <p>Idempotency rules:
 * <ul>
 *   <li>A file whose exact content (SHA-256) was already imported, and not removed since, is
 *       rejected as a duplicate.</li>
 *   <li>Rows whose natural key already exists for the same source are skipped and counted,
 *       so overlapping exports can be imported safely without double counting.</li>
 *   <li>A file with any invalid row is rejected as a whole; partial imports never happen.</li>
 * </ul>
 * Every attempt, successful or not, is recorded as a {@link CostImportJob}.
 */
@Service
public class CostIngestionService {

    static final String DEMO_COST_FILE = "demo-cost-data.csv";
    static final String DEMO_UTILIZATION_FILE = "demo-utilization.csv";
    private static final Logger log = LoggerFactory.getLogger(CostIngestionService.class);

    private final CostCsvParser costParser;
    private final UtilizationCsvParser utilizationParser;
    private final CostRecordWriter writer;
    private final CostImportJobRepository jobs;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public CostIngestionService(CostCsvParser costParser, UtilizationCsvParser utilizationParser,
            CostRecordWriter writer, CostImportJobRepository jobs, TransactionTemplate transactions,
            ApplicationEventPublisher events, Clock clock) {
        this.costParser = costParser;
        this.utilizationParser = utilizationParser;
        this.writer = writer;
        this.jobs = jobs;
        this.transactions = transactions;
        this.events = events;
        this.clock = clock;
    }

    public ImportOutcome importCostFile(CostSource source, String fileName, byte[] content) {
        return importFile(source, ImportDataType.COST, fileName, content, costParser::parse,
                (job, records) -> writer.insertCostRecords(job.getId(), source, records, Instant.now(clock)),
                records -> range(records, NormalizedCostRecord::usageDate, NormalizedCostRecord::usageDate));
    }

    public ImportOutcome importUtilizationFile(CostSource source, String fileName, byte[] content) {
        return importFile(source, ImportDataType.UTILIZATION, fileName, content, utilizationParser::parse,
                (job, records) -> writer.insertUtilization(job.getId(), records, Instant.now(clock)),
                records -> range(records, NormalizedUtilization::periodStart, NormalizedUtilization::periodEnd));
    }

    /** Loads the bundled, fictional demonstration dataset (cost data and utilization). */
    public List<ImportOutcome> loadDemoDataset() {
        byte[] costData = readDemoFile(DEMO_COST_FILE);
        if (jobs.findFirstByDataTypeAndFileChecksumAndStatus(ImportDataType.COST, sha256(costData),
                ImportStatus.COMPLETED).isPresent()) {
            throw new DuplicateImportException("The demonstration dataset is already loaded.");
        }
        List<ImportOutcome> outcomes = new ArrayList<>();
        outcomes.add(importCostFile(CostSource.DEMO_SAMPLE, DEMO_COST_FILE, costData));
        byte[] utilizationData = readDemoFile(DEMO_UTILIZATION_FILE);
        boolean utilizationLoaded = jobs.findFirstByDataTypeAndFileChecksumAndStatus(ImportDataType.UTILIZATION,
                sha256(utilizationData), ImportStatus.COMPLETED).isPresent();
        if (!utilizationLoaded) {
            outcomes.add(importUtilizationFile(CostSource.DEMO_SAMPLE, DEMO_UTILIZATION_FILE, utilizationData));
        }
        return outcomes;
    }

    /** Removes every completed demonstration import together with its records. */
    public int removeDemoData() {
        List<CostImportJob> demoJobs = jobs.findAllBySourceAndStatus(CostSource.DEMO_SAMPLE, ImportStatus.COMPLETED);
        demoJobs.forEach(job -> removeImport(job.getId()));
        return demoJobs.size();
    }

    /**
     * Deletes the records written by a completed CSV or demonstration import. Rows that the
     * import skipped because they already existed belong to the earlier import and remain.
     */
    public CostImportJob removeImport(long jobId) {
        CostImportJob removed = transactions.execute(status -> {
            CostImportJob job = jobs.findById(jobId).orElseThrow(() -> NotFoundException.of("Import job", jobId));
            if (!job.isRemovable()) {
                throw new InvalidRequestException("Only completed CSV and demonstration imports can be removed. "
                        + "Cost Explorer data is refreshed by running a new synchronization.");
            }
            int deleted = writer.deleteByImportJob(jobId);
            job.markRemoved(Instant.now(clock));
            log.info("Removed import job {} ({} rows deleted)", jobId, deleted);
            return job;
        });
        publishChange(removed);
        return removed;
    }

    public Page<CostImportJob> recentJobs(Pageable pageable) {
        return jobs.findAllByOrderByStartedAtDescIdDesc(pageable);
    }

    private <T> ImportOutcome importFile(CostSource source, ImportDataType dataType, String fileName, byte[] content,
            Function<byte[], ParseResult<T>> parser, RecordWriter<T> recordWriter,
            Function<List<T>, DateRange> periodOf) {
        if (source == CostSource.AWS_COST_EXPLORER) {
            throw new IllegalArgumentException("Cost Explorer data is ingested by the synchronization service");
        }
        String name = sanitizeFileName(fileName);
        String checksum = sha256(content == null ? new byte[0] : content);

        jobs.findFirstByDataTypeAndFileChecksumAndStatus(dataType, checksum, ImportStatus.COMPLETED)
                .ifPresent(existing -> rejectDuplicate(source, dataType, name, checksum, existing));

        ParseResult<T> parsed = parser.apply(content);
        if (parsed.hasErrors()) {
            CostImportJob job = CostImportJob.startFileImport(source, dataType, name, checksum, Instant.now(clock));
            job.fail(parsed.dataRows(), describe(parsed), Instant.now(clock));
            jobs.save(job);
            log.info("Rejected {} import of {}: {} validation error(s)", dataType, name, parsed.errors().size());
            return new ImportOutcome(job, parsed.errors(), parsed.errorsTruncated());
        }

        CostImportJob job;
        try {
            job = transactions.execute(status -> {
                CostImportJob running = jobs.save(
                        CostImportJob.startFileImport(source, dataType, name, checksum, Instant.now(clock)));
                running.setPeriod(periodOf.apply(parsed.records()));
                int received = parsed.records().size();
                int inserted = recordWriter.write(running, parsed.records());
                running.complete(received, inserted, received - inserted, 0, Instant.now(clock));
                return jobs.saveAndFlush(running);
            });
        } catch (DataIntegrityViolationException ex) {
            // Another request completed an import of the same content concurrently.
            throw new DuplicateImportException("Identical file content was imported concurrently; "
                    + "the duplicate was not applied.");
        }
        log.info("Imported {} from {} ({}): {} inserted, {} skipped", dataType, name, source,
                job.getRecordsInserted(), job.getRecordsSkipped());
        publishChange(job);
        return new ImportOutcome(job, List.of(), false);
    }

    private void rejectDuplicate(CostSource source, ImportDataType dataType, String name, String checksum,
            CostImportJob existing) {
        String message = "Identical file content was already imported by job #" + existing.getId() + " ("
                + existing.getFileName() + ", " + existing.getCompletedAt() + "). Remove that import first "
                + "if you need to import the file again.";
        CostImportJob job = CostImportJob.startFileImport(source, dataType, name, checksum, Instant.now(clock));
        job.fail(0, message, Instant.now(clock));
        jobs.save(job);
        throw new DuplicateImportException(message);
    }

    private void publishChange(CostImportJob job) {
        if (job.getPeriodStart() != null && job.getPeriodEnd() != null) {
            events.publishEvent(new CostDataChangedEvent(DateRange.of(job.getPeriodStart(), job.getPeriodEnd())));
        }
    }

    private static String describe(ParseResult<?> parsed) {
        String details = parsed.errors().stream().map(RowError::describe).collect(Collectors.joining("\n"));
        return parsed.errorsTruncated() ? details + "\n(further errors not shown)" : details;
    }

    private static <T> DateRange range(List<T> records, Function<T, LocalDate> startOf, Function<T, LocalDate> endOf) {
        LocalDate start = records.stream().map(startOf).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate end = records.stream().map(endOf).max(Comparator.naturalOrder()).orElseThrow();
        return DateRange.of(start, end);
    }

    static String sanitizeFileName(String fileName) {
        String name = StringUtils.getFilename(fileName == null ? "" : fileName.replace('\\', '/'));
        if (!StringUtils.hasText(name)) {
            return "upload.csv";
        }
        name = name.replaceAll("[\\p{Cntrl}]", "").strip();
        return name.length() > 255 ? name.substring(0, 255) : name;
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static byte[] readDemoFile(String name) {
        ClassPathResource resource = new ClassPathResource("demo/" + name);
        try (InputStream in = resource.getInputStream()) {
            return in.readAllBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException("Demonstration file " + name + " is not packaged with the application", ex);
        }
    }

    @FunctionalInterface
    private interface RecordWriter<T> {
        int write(CostImportJob job, List<T> records);
    }
}
