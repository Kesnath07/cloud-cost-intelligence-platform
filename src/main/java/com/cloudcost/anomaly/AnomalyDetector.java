package com.cloudcost.anomaly;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.SortedMap;

import com.cloudcost.analysis.CostDimension;
import com.cloudcost.common.DateRange;
import com.cloudcost.common.MoneyMath;
import com.cloudcost.common.Severity;

/**
 * Rule-based detector for unusual daily cost increases. No statistical model or machine
 * learning is involved; every finding can be reproduced by hand from its figures:
 *
 * <ol>
 *   <li>Baseline: the median daily cost on the days <em>with data</em> in the
 *       {@code baselineDays} calendar days before the evaluated day. The median is used
 *       because a single earlier spike barely moves it.</li>
 *   <li>Days with fewer than {@code minBaselineDays} baseline days are not evaluated.</li>
 *   <li>A day is flagged when {@code observed - baseline >= minAbsoluteIncrease} and, if the
 *       baseline is positive, {@code (observed - baseline) / baseline >= minPercentIncrease}.
 *       With a zero or negative baseline only the absolute rule applies ("new spend").</li>
 *   <li>Severity depends on the absolute increase only, because that is what affects the bill.</li>
 * </ol>
 */
public class AnomalyDetector {

    private final AnomalyProperties properties;
    private final String currency;

    public AnomalyDetector(AnomalyProperties properties, String currency) {
        this.properties = properties;
        this.currency = currency;
    }

    /**
     * @param series daily costs of one dimension value; must contain an entry (zero if no spend)
     *               for every day that has any cost data, and no entry for days without data
     * @param evaluation the days to evaluate; the series should start {@code baselineDays} earlier
     */
    public List<AnomalyFinding> detect(CostDimension dimension, String value, NavigableMap<LocalDate, BigDecimal> series,
            DateRange evaluation) {
        List<AnomalyFinding> findings = new ArrayList<>();
        for (LocalDate date : series.subMap(evaluation.start(), true, evaluation.end(), true).keySet()) {
            SortedMap<LocalDate, BigDecimal> window =
                    series.subMap(date.minusDays(properties.baselineDays()), true, date, false);
            if (window.size() < properties.minBaselineDays()) {
                continue;
            }
            BigDecimal observed = series.get(date);
            BigDecimal baseline = median(new ArrayList<>(window.values()));
            BigDecimal increase = observed.subtract(baseline);
            if (increase.compareTo(properties.minAbsoluteIncrease()) < 0) {
                continue;
            }
            BigDecimal percent = baseline.signum() > 0 ? MoneyMath.percentChange(observed, baseline) : null;
            if (percent != null && percent.compareTo(properties.minPercentIncrease()) < 0) {
                continue;
            }
            findings.add(new AnomalyFinding(dimension, value, date, observed, baseline, increase, percent,
                    window.size(), severity(increase), reason(observed, baseline, increase, percent, window.size())));
        }
        return findings;
    }

    Severity severity(BigDecimal increase) {
        if (increase.compareTo(properties.highIncrease()) >= 0) {
            return Severity.HIGH;
        }
        if (increase.compareTo(properties.mediumIncrease()) >= 0) {
            return Severity.MEDIUM;
        }
        return Severity.LOW;
    }

    static BigDecimal median(List<BigDecimal> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Cannot compute the median of no values");
        }
        List<BigDecimal> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }
        return sorted.get(middle - 1).add(sorted.get(middle)).divide(BigDecimal.valueOf(2), 6, RoundingMode.HALF_UP);
    }

    private String reason(BigDecimal observed, BigDecimal baseline, BigDecimal increase, BigDecimal percent,
            int baselineDays) {
        String window = "the median of the previous " + baselineDays + " day(s) with data";
        if (percent == null) {
            return "Daily cost of " + money(observed) + " compared with " + window + " of " + money(baseline)
                    + " (no positive baseline, so this is new or previously offset spend). Flagged because the "
                    + "increase of " + money(increase) + " is at least " + money(properties.minAbsoluteIncrease()) + ".";
        }
        return "Daily cost of " + money(observed) + " was " + money(increase) + " (+" + percent.toPlainString()
                + "%) above " + window + " (" + money(baseline) + "). Flagged because the increase is at least "
                + properties.minPercentIncrease().stripTrailingZeros().toPlainString() + "% and at least "
                + money(properties.minAbsoluteIncrease()) + ".";
    }

    private String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString() + " " + currency;
    }
}
