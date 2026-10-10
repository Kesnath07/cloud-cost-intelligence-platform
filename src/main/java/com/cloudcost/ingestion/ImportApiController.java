package com.cloudcost.ingestion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import com.cloudcost.common.NotFoundException;
import com.cloudcost.costdata.CostImportJobRepository;
import com.cloudcost.costdata.CostSource;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/imports")
class ImportApiController {

    private static final int MAX_PAGE_SIZE = 100;

    private final CostIngestionService ingestion;
    private final CostImportJobRepository jobs;

    ImportApiController(CostIngestionService ingestion, CostImportJobRepository jobs) {
        this.ingestion = ingestion;
        this.jobs = jobs;
    }

    @GetMapping
    Page<ImportJobResponse> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest request = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        return ingestion.recentJobs(request).map(ImportJobResponse::from);
    }

    @GetMapping("/{id}")
    ImportJobResponse get(@PathVariable long id) {
        return jobs.findById(id).map(ImportJobResponse::from)
                .orElseThrow(() -> NotFoundException.of("Import job", id));
    }

    @PostMapping("/costs")
    ResponseEntity<Object> importCosts(@RequestPart("file") MultipartFile file) {
        return respond(ingestion.importCostFile(CostSource.CSV_UPLOAD, file.getOriginalFilename(), bytes(file)));
    }

    @PostMapping("/utilization")
    ResponseEntity<Object> importUtilization(@RequestPart("file") MultipartFile file) {
        return respond(ingestion.importUtilizationFile(CostSource.CSV_UPLOAD, file.getOriginalFilename(), bytes(file)));
    }

    @PostMapping("/demo")
    ResponseEntity<List<ImportJobResponse>> loadDemo() {
        List<ImportJobResponse> jobs = ingestion.loadDemoDataset().stream()
                .map(outcome -> ImportJobResponse.from(outcome.job()))
                .toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(jobs);
    }

    @DeleteMapping("/demo")
    ResponseEntity<Void> removeDemo() {
        ingestion.removeDemoData();
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    ImportJobResponse remove(@PathVariable long id) {
        return ImportJobResponse.from(ingestion.removeImport(id));
    }

    private static ResponseEntity<Object> respond(ImportOutcome outcome) {
        if (outcome.succeeded()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(ImportJobResponse.from(outcome.job()));
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, outcome.summary());
        problem.setTitle("Import validation failed");
        problem.setProperty("importJobId", outcome.job().getId());
        problem.setProperty("errors", outcome.errors().stream().map(RowError::describe).toList());
        problem.setProperty("errorsTruncated", outcome.errorsTruncated());
        return ResponseEntity.badRequest().body(problem);
    }

    static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read the uploaded file", ex);
        }
    }
}
