package com.cloudcost.recommendation;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OptimizationRecommendationRepository extends JpaRepository<OptimizationRecommendation, Long> {

    Page<OptimizationRecommendation> findAllByStatus(RecommendationStatus status, Pageable pageable);

    List<OptimizationRecommendation> findAllByStatus(RecommendationStatus status);

    List<OptimizationRecommendation> findAllByStatusIn(List<RecommendationStatus> statuses);

    long countByStatus(RecommendationStatus status);
}
