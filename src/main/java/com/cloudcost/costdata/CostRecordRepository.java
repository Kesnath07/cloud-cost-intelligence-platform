package com.cloudcost.costdata;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CostRecordRepository extends JpaRepository<CostRecord, Long> {

    long countByImportJobId(Long importJobId);
}
