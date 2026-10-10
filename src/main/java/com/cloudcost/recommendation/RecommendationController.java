package com.cloudcost.recommendation;

import com.cloudcost.common.InvalidRequestException;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/recommendations")
class RecommendationController {

    private static final int PAGE_SIZE = 20;

    private final RecommendationService recommendations;

    RecommendationController(RecommendationService recommendations) {
        this.recommendations = recommendations;
    }

    @GetMapping
    String list(@RequestParam(defaultValue = "OPEN") String status, @RequestParam(defaultValue = "0") int page,
            Model model) {
        RecommendationStatus filter = "ALL".equals(status) ? null : parse(status);
        model.addAttribute("recommendations", recommendations.list(filter,
                PageRequest.of(Math.max(page, 0), PAGE_SIZE, RecommendationApiController.DEFAULT_SORT)));
        model.addAttribute("status", filter == null ? "ALL" : filter.name());
        model.addAttribute("statuses", RecommendationStatus.values());
        return "recommendations";
    }

    @PostMapping("/generate")
    String generate(RedirectAttributes redirect) {
        RecommendationService.GenerationResult result = recommendations.generate();
        redirect.addFlashAttribute("notice", "Evaluated all rules: " + result.candidates() + " current finding(s), "
                + result.created() + " new, " + result.removed() + " open recommendation(s) no longer supported "
                + "by the data were removed.");
        return "redirect:/recommendations";
    }

    @PostMapping("/{id}/status")
    String changeStatus(@PathVariable long id, @RequestParam RecommendationStatus status,
            @RequestParam(required = false) String note, @RequestParam(defaultValue = "OPEN") String returnStatus,
            RedirectAttributes redirect) {
        if (note != null && note.length() > 500) {
            redirect.addFlashAttribute("errorMessage", "Notes are limited to 500 characters.");
        } else {
            recommendations.changeStatus(id, status, note);
            redirect.addFlashAttribute("notice", "Recommendation #" + id + " marked as " + status + ".");
        }
        redirect.addAttribute("status", returnStatus);
        return "redirect:/recommendations";
    }

    private static RecommendationStatus parse(String value) {
        try {
            return RecommendationStatus.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("Unknown recommendation status '" + value + "'");
        }
    }
}
