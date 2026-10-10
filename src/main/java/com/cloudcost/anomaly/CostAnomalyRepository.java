package com.cloudcost.anomaly;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CostAnomalyRepository extends JpaRepository<CostAnomaly, Long> {

    List<CostAnomaly> findAllByAnomalyDateBetween(LocalDate start, LocalDate end);

    Page<CostAnomaly> findAllByStatus(AnomalyStatus status, Pageable pageable);

    long countByStatus(AnomalyStatus status);

    List<CostAnomaly> findAllByAnomalyDateBetweenOrderByAnomalyDateDescIncreaseAmountDesc(LocalDate start,
            LocalDate end);
}
