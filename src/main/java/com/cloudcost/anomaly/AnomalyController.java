package com.cloudcost.anomaly;

import java.time.LocalDate;

import com.cloudcost.common.DateRange;
import com.cloudcost.common.InvalidRequestException;

import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/anomalies")
class AnomalyController {

    private static final int PAGE_SIZE = 25;

    private final AnomalyDetectionService detection;
    private final AnomalyProperties properties;

    AnomalyController(AnomalyDetectionService detection, AnomalyProperties properties) {
        this.detection = detection;
        this.properties = properties;
    }

    @GetMapping
    String list(@RequestParam(defaultValue = "OPEN") String status, @RequestParam(defaultValue = "0") int page,
            Model model) {
        AnomalyStatus filter = "ALL".equals(status) ? null : parseStatus(status);
        model.addAttribute("anomalies", detection.list(filter,
                PageRequest.of(Math.max(page, 0), PAGE_SIZE, AnomalyApiController.NEWEST_FIRST)));
        model.addAttribute("status", filter == null ? "ALL" : filter.name());
        model.addAttribute("statuses", AnomalyStatus.values());
        model.addAttribute("rules", properties);
        model.addAttribute("defaultRange", detection.defaultEvaluationRange());
        return "anomalies";
    }

    @PostMapping("/detect")
    String detect(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate end,
            RedirectAttributes redirect) {
        try {
            DateRange range = start == null && end == null ? detection.defaultEvaluationRange()
                    : DateRange.ofRequest(start, end);
            AnomalyDetectionService.DetectionResult result = detection.detect(range);
            redirect.addFlashAttribute("notice", "Evaluated " + range + ": " + result.findings()
                    + " anomalous day(s), " + result.created() + " new, " + result.removed()
                    + " open finding(s) no longer supported by the data were removed.");
        } catch (InvalidRequestException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/anomalies";
    }

    @PostMapping("/{id}/review")
    String review(@PathVariable long id, @RequestParam AnomalyStatus status,
            @RequestParam(required = false) String note, @RequestParam(defaultValue = "OPEN") String returnStatus,
            RedirectAttributes redirect) {
        if (note != null && note.length() > 500) {
            redirect.addFlashAttribute("errorMessage", "Review notes are limited to 500 characters.");
        } else {
            detection.review(id, status, note);
            redirect.addFlashAttribute("notice", "Anomaly #" + id + " marked as " + status + ".");
        }
        redirect.addAttribute("status", returnStatus);
        return "redirect:/anomalies";
    }

    private static AnomalyStatus parseStatus(String value) {
        try {
            return AnomalyStatus.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("Unknown anomaly status '" + value + "'");
        }
    }
}
