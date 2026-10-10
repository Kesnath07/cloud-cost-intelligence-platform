package com.cloudcost.recommendation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.cloudcost.common.Severity;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
class RecommendationApiController {

    /** Most recently evaluated first; severity is shown prominently on every item. */
    static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));

    private final RecommendationService recommendations;

    RecommendationApiController(RecommendationService recommendations) {
        this.recommendations = recommendations;
    }

    record RecommendationResponse(long id, String title, RecommendationCategory category, Severity severity,
            String serviceName, String resourceId, String environment, EvidenceSource evidenceSource,
            List<String> evidence, String explanation, String suggestedAction, BigDecimal estimatedMonthlySavings,
            String savingsBasis, LocalDate periodStart, LocalDate periodEnd, RecommendationStatus status,
            String statusNote, Instant createdAt, Instant lastEvaluatedAt) {

        static RecommendationResponse from(OptimizationRecommendation r) {
            return new RecommendationResponse(r.getId(), r.getTitle(), r.getCategory(), r.getSeverity(),
                    r.getServiceName(), r.getResourceId(), r.getEnvironment(), r.getEvidenceSource(),
                    r.evidenceLines(), r.getExplanation(), r.getSuggestedAction(), r.getEstimatedMonthlySavings(),
                    r.getSavingsBasis(), r.getPeriodStart(), r.getPeriodEnd(), r.getStatus(), r.getStatusNote(),
                    r.getCreatedAt(), r.getLastEvaluatedAt());
        }
    }

    @GetMapping
    Page<RecommendationResponse> list(@RequestParam(required = false) RecommendationStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        PageRequest request = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), DEFAULT_SORT);
        return recommendations.list(status, request).map(RecommendationResponse::from);
    }

    @GetMapping("/{id}")
    RecommendationResponse get(@PathVariable long id) {
        return RecommendationResponse.from(recommendations.get(id));
    }

    @PostMapping("/generate")
    RecommendationService.GenerationResult generate() {
        return recommendations.generate();
    }

    @PatchMapping("/{id}")
    RecommendationResponse changeStatus(@PathVariable long id, @Valid @RequestBody RecommendationStatusRequest request) {
        return RecommendationResponse.from(recommendations.changeStatus(id, request.status(), request.note()));
    }
}
