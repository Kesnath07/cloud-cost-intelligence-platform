package com.cloudcost.costdata;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CostImportJobRepository extends JpaRepository<CostImportJob, Long> {

    Page<CostImportJob> findAllByOrderByStartedAtDescIdDesc(Pageable pageable);

    Optional<CostImportJob> findFirstByDataTypeAndFileChecksumAndStatus(ImportDataType dataType, String fileChecksum,
            ImportStatus status);
}
