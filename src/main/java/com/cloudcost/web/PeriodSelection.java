package com.cloudcost.web;

import java.time.LocalDate;

import com.cloudcost.analysis.CostAnalysisService;
import com.cloudcost.common.DateRange;
import com.cloudcost.common.InvalidRequestException;

import org.springframework.ui.Model;

/** Resolves the period chosen on a page, falling back to the default with a message when it is invalid. */
final class PeriodSelection {

    private PeriodSelection() {
    }

    static DateRange resolve(CostAnalysisService analysis, LocalDate start, LocalDate end, Model model) {
        DateRange range;
        try {
            range = analysis.resolveRange(start, end);
        } catch (InvalidRequestException ex) {
            model.addAttribute("errorMessage", ex.getMessage() + ". Showing the default period instead.");
            range = analysis.resolveRange(null, null);
        }
        model.addAttribute("range", range);
        model.addAttribute("defaultRange", start == null && end == null);
        return range;
    }
}
