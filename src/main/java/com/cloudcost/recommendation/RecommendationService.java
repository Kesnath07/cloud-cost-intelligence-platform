package com.cloudcost.recommendation;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cloudcost.budget.BudgetChangedEvent;
import com.cloudcost.common.NotFoundException;
import com.cloudcost.costdata.CostDataChangedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Regenerates recommendations and reconciles them with stored ones by fingerprint: new
 * candidates are stored as OPEN, existing OPEN or ACCEPTED ones get fresh evidence, reviewed
 * (DISMISSED or RESOLVED) ones are left untouched, and OPEN recommendations that the current
 * data no longer supports are deleted.
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    /** Display order for lists and exports: severity (highest first), then title and id. */
    public static final Comparator<OptimizationRecommendation> MOST_SEVERE_FIRST =
            Comparator.comparing(OptimizationRecommendation::getSeverity).reversed()
                    .thenComparing(OptimizationRecommendation::getTitle)
                    .thenComparing(OptimizationRecommendation::getId);

    private final RecommendationEngine engine;
    private final OptimizationRecommendationRepository recommendations;
    private final TransactionTemplate newTransaction;
    private final Clock clock;

    public RecommendationService(RecommendationEngine engine, OptimizationRecommendationRepository recommendations,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.engine = engine;
        this.recommendations = recommendations;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    public record GenerationResult(int candidates, int created, int refreshed, int removed) {
    }

    @Transactional
    public GenerationResult generate() {
        List<RecommendationCandidate> candidates = engine.run();
        Instant now = Instant.now(clock);
        Map<String, OptimizationRecommendation> existing = new HashMap<>();
        recommendations.findAll().forEach(r -> existing.put(r.getFingerprint(), r));

        int created = 0;
        int refreshed = 0;
        for (RecommendationCandidate candidate : candidates) {
            OptimizationRecommendation stored = existing.remove(candidate.fingerprint());
            if (stored == null) {
                recommendations.save(OptimizationRecommendation.open(candidate, now));
                created++;
            } else if (stored.getStatus() == RecommendationStatus.OPEN
                    || stored.getStatus() == RecommendationStatus.ACCEPTED) {
                stored.refresh(candidate, now);
                refreshed++;
            }
        }
        List<OptimizationRecommendation> unsupported = existing.values().stream()
                .filter(r -> r.getStatus() == RecommendationStatus.OPEN)
                .toList();
        recommendations.deleteAll(unsupported);

        GenerationResult result = new GenerationResult(candidates.size(), created, refreshed, unsupported.size());
        log.info("Generated recommendations: {}", result);
        return result;
    }

    @Order(2)
    @EventListener
    public void onCostDataChanged(CostDataChangedEvent event) {
        generate();
    }

    /** Budgets feed the budget rule; regenerate once the budget change is committed. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBudgetChanged(BudgetChangedEvent event) {
        newTransaction.executeWithoutResult(status -> generate());
    }

    @Transactional(readOnly = true)
    public Page<OptimizationRecommendation> list(RecommendationStatus status, Pageable pageable) {
        return status == null ? recommendations.findAll(pageable) : recommendations.findAllByStatus(status, pageable);
    }

    @Transactional(readOnly = true)
    public OptimizationRecommendation get(long id) {
        return recommendations.findById(id).orElseThrow(() -> NotFoundException.of("Recommendation", id));
    }

    @Transactional(readOnly = true)
    public long countOpen() {
        return recommendations.countByStatus(RecommendationStatus.OPEN);
    }

    @Transactional(readOnly = true)
    public List<OptimizationRecommendation> findActive() {
        return recommendations.findAllByStatusIn(List.of(RecommendationStatus.OPEN, RecommendationStatus.ACCEPTED));
    }

    /** Every stored recommendation, or those with one status, most severe first. */
    @Transactional(readOnly = true)
    public List<OptimizationRecommendation> findAll(RecommendationStatus status) {
        List<OptimizationRecommendation> found = status == null ? recommendations.findAll()
                : recommendations.findAllByStatus(status);
        return found.stream().sorted(MOST_SEVERE_FIRST).toList();
    }

    @Transactional
    public OptimizationRecommendation changeStatus(long id, RecommendationStatus status, String note) {
        OptimizationRecommendation recommendation = get(id);
        recommendation.changeStatus(status, note, Instant.now(clock));
        return recommendation;
    }
}
