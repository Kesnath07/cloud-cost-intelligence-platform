package com.cloudcost.costdata;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceUtilizationRepository extends JpaRepository<ResourceUtilization, Long> {

    long countByImportJobId(Long importJobId);
}
