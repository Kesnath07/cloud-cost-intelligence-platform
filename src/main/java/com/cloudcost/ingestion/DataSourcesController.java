package com.cloudcost.ingestion;

import java.util.List;
import java.util.function.Supplier;

import com.cloudcost.common.ConflictException;
import com.cloudcost.common.InvalidRequestException;
import com.cloudcost.costdata.CostRecordRepository;
import com.cloudcost.costdata.CostSource;
import com.cloudcost.costdata.ResourceUtilizationRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/data-sources")
class DataSourcesController {

    private static final int PAGE_SIZE = 15;

    private final CostIngestionService ingestion;
    private final CostRecordRepository records;
    private final ResourceUtilizationRepository utilization;

    DataSourcesController(CostIngestionService ingestion, CostRecordRepository records,
            ResourceUtilizationRepository utilization) {
        this.ingestion = ingestion;
        this.records = records;
        this.utilization = utilization;
    }

    @GetMapping
    String dataSources(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("coverage", records.coverageBySource());
        model.addAttribute("utilizationCount", utilization.count());
        model.addAttribute("jobs", ingestion.recentJobs(PageRequest.of(Math.max(page, 0), PAGE_SIZE)));
        model.addAttribute("costColumns", CostCsvParser.REQUIRED_COLUMNS);
        model.addAttribute("costOptionalColumns", CostCsvParser.OPTIONAL_COLUMNS);
        model.addAttribute("utilizationColumns", UtilizationCsvParser.REQUIRED_COLUMNS);
        model.addAttribute("utilizationOptionalColumns", UtilizationCsvParser.OPTIONAL_COLUMNS);
        return "data-sources";
    }

    @PostMapping("/costs")
    String uploadCosts(@RequestParam("file") MultipartFile file, RedirectAttributes redirect) {
        return handleUpload(redirect, () -> ingestion.importCostFile(CostSource.CSV_UPLOAD,
                file.getOriginalFilename(), ImportApiController.bytes(file)));
    }

    @PostMapping("/utilization")
    String uploadUtilization(@RequestParam("file") MultipartFile file, RedirectAttributes redirect) {
        return handleUpload(redirect, () -> ingestion.importUtilizationFile(CostSource.CSV_UPLOAD,
                file.getOriginalFilename(), ImportApiController.bytes(file)));
    }

    @PostMapping("/demo")
    String loadDemo(RedirectAttributes redirect) {
        try {
            List<ImportOutcome> outcomes = ingestion.loadDemoDataset();
            redirect.addFlashAttribute("notice", "Demonstration dataset loaded. "
                    + String.join(" ", outcomes.stream().map(ImportOutcome::summary).toList()));
        } catch (ConflictException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/data-sources";
    }

    @PostMapping("/demo/remove")
    String removeDemo(RedirectAttributes redirect) {
        int removed = ingestion.removeDemoData();
        redirect.addFlashAttribute("notice", removed == 0
                ? "No demonstration data was loaded."
                : "Removed the demonstration dataset (" + removed + " import(s)).");
        return "redirect:/data-sources";
    }

    @PostMapping("/imports/{id}/remove")
    String removeImport(@PathVariable long id, RedirectAttributes redirect) {
        try {
            ingestion.removeImport(id);
            redirect.addFlashAttribute("notice", "Removed the records of import #" + id + ".");
        } catch (InvalidRequestException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/data-sources";
    }

    private static String handleUpload(RedirectAttributes redirect, Supplier<ImportOutcome> upload) {
        try {
            ImportOutcome outcome = upload.get();
            if (outcome.succeeded()) {
                redirect.addFlashAttribute("notice", outcome.summary());
            } else {
                redirect.addFlashAttribute("errorMessage", outcome.summary());
                redirect.addFlashAttribute("importErrors", outcome.errors().stream().map(RowError::describe).toList());
                redirect.addFlashAttribute("importErrorsTruncated", outcome.errorsTruncated());
            }
        } catch (ConflictException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/data-sources";
    }
}
