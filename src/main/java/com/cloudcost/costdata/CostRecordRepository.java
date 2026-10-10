package com.cloudcost.costdata;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CostRecordRepository extends JpaRepository<CostRecord, Long> {

    long countByImportJobId(Long importJobId);

    boolean existsBySource(CostSource source);

    @Query("""
            select new com.cloudcost.costdata.SourceCoverage(r.source, count(r), min(r.usageDate), max(r.usageDate))
            from CostRecord r
            group by r.source
            order by r.source
            """)
    List<SourceCoverage> coverageBySource();
}
