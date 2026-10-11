package com.cloudcost.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.cloudcost.common.DateRange;
import com.cloudcost.common.MoneyMath;
import com.cloudcost.costdata.CostRecord;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregation queries over cost records. Sums are computed by PostgreSQL on NUMERIC columns,
 * so no floating-point arithmetic is involved.
 */
@Repository
@Transactional(readOnly = true)
public class CostAggregationRepository {

    private static final String USAGE_DATE = "usageDate";
    private static final String AMOUNT = "amount";

    private final EntityManager entityManager;

    public CostAggregationRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public record GroupTotal(String key, BigDecimal amount) {
    }

    public record DailyGroupTotal(LocalDate date, String key, BigDecimal amount) {
    }

    public record DailyServiceEnvironmentTotal(LocalDate date, String service, String environment,
            BigDecimal amount) {
    }

    public BigDecimal total(DateRange range, CostFilter filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<BigDecimal> query = cb.createQuery(BigDecimal.class);
        Root<CostRecord> root = query.from(CostRecord.class);
        query.select(cb.sum(root.<BigDecimal>get(AMOUNT))).where(predicates(cb, root, range, filter));
        return MoneyMath.zeroIfNull(entityManager.createQuery(query).getSingleResult());
    }

    /** Totals per day; days without matching records are absent from the map. */
    public Map<LocalDate, BigDecimal> dailyTotals(DateRange range, CostFilter filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<CostRecord> root = query.from(CostRecord.class);
        Path<LocalDate> date = root.get(USAGE_DATE);
        query.multiselect(date, cb.sum(root.<BigDecimal>get(AMOUNT)))
                .where(predicates(cb, root, range, filter))
                .groupBy(date)
                .orderBy(cb.asc(date));
        Map<LocalDate, BigDecimal> totals = new LinkedHashMap<>();
        for (Tuple tuple : entityManager.createQuery(query).getResultList()) {
            totals.put(tuple.get(0, LocalDate.class), tuple.get(1, BigDecimal.class));
        }
        return totals;
    }

    public List<GroupTotal> totalsBy(CostDimension dimension, DateRange range, CostFilter filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<CostRecord> root = query.from(CostRecord.class);
        Path<String> key = root.get(dimension.attribute());
        Expression<BigDecimal> sum = cb.sum(root.<BigDecimal>get(AMOUNT));
        query.multiselect(key, sum).where(predicates(cb, root, range, filter)).groupBy(key);
        List<GroupTotal> totals = new ArrayList<>();
        for (Tuple tuple : entityManager.createQuery(query).getResultList()) {
            totals.add(new GroupTotal(tuple.get(0, String.class), tuple.get(1, BigDecimal.class)));
        }
        return totals;
    }

    public List<DailyGroupTotal> dailyTotalsBy(CostDimension dimension, DateRange range, CostFilter filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<CostRecord> root = query.from(CostRecord.class);
        Path<LocalDate> date = root.get(USAGE_DATE);
        Path<String> key = root.get(dimension.attribute());
        query.multiselect(date, key, cb.sum(root.<BigDecimal>get(AMOUNT)))
                .where(predicates(cb, root, range, filter))
                .groupBy(date, key)
                .orderBy(cb.asc(date));
        List<DailyGroupTotal> totals = new ArrayList<>();
        for (Tuple tuple : entityManager.createQuery(query).getResultList()) {
            totals.add(new DailyGroupTotal(tuple.get(0, LocalDate.class), tuple.get(1, String.class),
                    tuple.get(2, BigDecimal.class)));
        }
        return totals;
    }

    public List<DailyServiceEnvironmentTotal> dailyTotalsByServiceAndEnvironment(DateRange range,
            CostFilter filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<CostRecord> root = query.from(CostRecord.class);
        Path<LocalDate> date = root.get(USAGE_DATE);
        Path<String> service = root.get(CostDimension.SERVICE.attribute());
        Path<String> environment = root.get(CostDimension.ENVIRONMENT.attribute());
        query.multiselect(date, service, environment, cb.sum(root.<BigDecimal>get(AMOUNT)))
                .where(predicates(cb, root, range, filter))
                .groupBy(date, service, environment)
                .orderBy(cb.asc(date), cb.asc(service), cb.asc(environment));
        List<DailyServiceEnvironmentTotal> totals = new ArrayList<>();
        for (Tuple tuple : entityManager.createQuery(query).getResultList()) {
            totals.add(new DailyServiceEnvironmentTotal(tuple.get(0, LocalDate.class), tuple.get(1, String.class),
                    tuple.get(2, String.class), tuple.get(3, BigDecimal.class)));
        }
        return totals;
    }

    /**
     * Days in the range for which any cost data was ingested, regardless of filters. A day in
     * this set with no matching records is a genuine zero; a day outside it is a data gap.
     */
    public Set<LocalDate> datesWithData(DateRange range) {
        return new TreeSet<>(entityManager.createQuery("""
                        select distinct r.usageDate from CostRecord r
                        where r.usageDate between :start and :end
                        """, LocalDate.class)
                .setParameter("start", range.start())
                .setParameter("end", range.end())
                .getResultList());
    }

    public boolean containsEstimatedData(DateRange range) {
        return !entityManager.createQuery("""
                        select r.id from CostRecord r
                        where r.estimated = true and r.usageDate between :start and :end
                        """, Long.class)
                .setParameter("start", range.start())
                .setParameter("end", range.end())
                .setMaxResults(1)
                .getResultList()
                .isEmpty();
    }

    public DataCoverage coverage() {
        Object[] row = entityManager.createQuery(
                        "select min(r.usageDate), max(r.usageDate), count(r) from CostRecord r", Object[].class)
                .getSingleResult();
        return new DataCoverage((LocalDate) row[0], (LocalDate) row[1], (Long) row[2]);
    }

    public List<String> distinctValues(CostDimension dimension) {
        return entityManager.createQuery("select distinct r." + dimension.attribute() + " from CostRecord r order by 1",
                String.class).getResultList();
    }

    private static Predicate[] predicates(CriteriaBuilder cb, Root<CostRecord> root, DateRange range,
            CostFilter filter) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.between(root.get(USAGE_DATE), range.start(), range.end()));
        if (filter.service() != null) {
            predicates.add(cb.equal(root.get(CostDimension.SERVICE.attribute()), filter.service()));
        }
        if (filter.environment() != null) {
            predicates.add(cb.equal(root.get(CostDimension.ENVIRONMENT.attribute()), filter.environment()));
        }
        return predicates.toArray(Predicate[]::new);
    }
}
