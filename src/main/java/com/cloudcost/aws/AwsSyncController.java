package com.cloudcost.aws;

import java.time.LocalDate;

import com.cloudcost.common.ConflictException;
import com.cloudcost.common.InvalidRequestException;
import com.cloudcost.common.ServiceUnavailableException;
import com.cloudcost.costdata.CostImportJob;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
class AwsSyncController {

    private final CostExplorerSyncService syncService;

    AwsSyncController(CostExplorerSyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/data-sources/aws-sync")
    String sync(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            RedirectAttributes redirect) {
        try {
            CostImportJob job = syncService.sync(start, end);
            redirect.addFlashAttribute("notice", "Cost Explorer synchronization #" + job.getId() + " stored "
                    + job.getRecordsInserted() + " record(s) for " + start + " to " + end + " (replaced "
                    + job.getRecordsReplaced() + " previously synchronized record(s)).");
        } catch (InvalidRequestException | ConflictException | ServiceUnavailableException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/data-sources";
    }
}
