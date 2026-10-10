package com.cloudcost.budget;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<CostBudget, Long> {

    List<CostBudget> findAllByOrderByNameAsc();

    List<CostBudget> findAllByActiveTrueOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
