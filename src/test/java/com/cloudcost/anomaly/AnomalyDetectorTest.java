package com.cloudcost.anomaly;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.TreeMap;

import com.cloudcost.analysis.CostDimension;
import com.cloudcost.common.DateRange;
import com.cloudcost.common.Severity;

import org.junit.jupiter.api.Test;

class AnomalyDetectorTest {

    private static final LocalDate DAY_15 = LocalDate.of(2026, 9, 15);
    private static final AnomalyProperties DEFAULTS = new AnomalyProperties(14, 7, new BigDecimal("50"),
            new BigDecimal("25.00"), new BigDecimal("100.00"), new BigDecimal("500.00"));

    private final AnomalyDetector detector = new AnomalyDetector(DEFAULTS, "USD");

    /** Fourteen days of {@code baseline} on Sep 1-14 followed by {@code observed} on Sep 15. */
    private static TreeMap<LocalDate, BigDecimal> series(String baseline, String observed) {
        TreeMap<LocalDate, BigDecimal> series = new TreeMap<>();
        for (int day = 1; day <= 14; day++) {
            series.put(LocalDate.of(2026, 9, day), new BigDecimal(baseline));
        }
        series.put(DAY_15, new BigDecimal(observed));
        return series;
    }

    private List<AnomalyFinding> detect(TreeMap<LocalDate, BigDecimal> series) {
        return detector.detect(CostDimension.SERVICE, "EC2 - Other", series, DateRange.of(DAY_15, DAY_15));
    }

    @Test
    void flagsAnIncreaseAboveBothThresholdsWithExplainableFigures() {
        List<AnomalyFinding> findings = detect(series("18.00", "162.40"));

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.date()).isEqualTo(DAY_15);
            assertThat(finding.baseline()).isEqualByComparingTo("18.00");
            assertThat(finding.increase()).isEqualByComparingTo("144.40");
            assertThat(finding.increasePercent()).isEqualByComparingTo("802.22");
            assertThat(finding.baselineDays()).isEqualTo(14);
            assertThat(finding.severity()).isEqualTo(Severity.MEDIUM);
            assertThat(finding.reason())
                    .contains("162.40 USD", "144.40 USD", "+802.22%", "median of the previous 14 day(s)",
                            "at least 50% and at least 25.00 USD");
        });
    }

    @Test
    void ignoresLargeRelativeChangesOnSmallAmounts() {
        assertThat(detect(series("2.00", "20.00"))).as("+900% but only +18.00").isEmpty();
    }

    @Test
    void ignoresLargeAbsoluteChangesThatAreSmallRelativeToTheBaseline() {
        assertThat(detect(series("1000.00", "1400.00"))).as("+400.00 but only +40%").isEmpty();
    }

    @Test
    void flagsExactlyAtTheThresholds() {
        assertThat(detect(series("50.00", "75.00"))).as("+50% and +25.00").hasSize(1);
        assertThat(detect(series("50.00", "74.99"))).isEmpty();
    }

    @Test
    void reportsNewSpendWhenThereIsNoPositiveBaseline() {
        assertThat(detect(series("0", "40.00"))).singleElement().satisfies(finding -> {
            assertThat(finding.increasePercent()).isNull();
            assertThat(finding.reason()).contains("no positive baseline");
        });
        assertThat(detect(series("-5.00", "30.00"))).as("baseline of net credits").hasSize(1);
        assertThat(detect(series("0", "20.00"))).as("below the absolute threshold").isEmpty();
    }

    @Test
    void requiresEnoughBaselineDaysWithData() {
        TreeMap<LocalDate, BigDecimal> sparse = new TreeMap<>();
        for (int day = 9; day <= 14; day++) {
            sparse.put(LocalDate.of(2026, 9, day), new BigDecimal("10"));
        }
        sparse.put(DAY_15, new BigDecimal("500"));

        assertThat(detect(sparse)).as("only 6 baseline days").isEmpty();
    }

    @Test
    void daysWithoutDataAreNeitherEvaluatedNorPartOfTheBaseline() {
        TreeMap<LocalDate, BigDecimal> series = series("10.00", "100.00");
        series.remove(LocalDate.of(2026, 9, 14));

        List<AnomalyFinding> findings = detector.detect(CostDimension.SERVICE, "EC2", series,
                DateRange.of(LocalDate.of(2026, 9, 14), DAY_15));

        assertThat(findings).singleElement().satisfies(finding -> assertThat(finding.baselineDays()).isEqualTo(13));
    }

    @Test
    void medianBaselineIsRobustToAnEarlierSpikeSoConsecutiveSpikeDaysAreFlagged() {
        TreeMap<LocalDate, BigDecimal> series = series("18.00", "160.00");
        series.put(LocalDate.of(2026, 9, 16), new BigDecimal("150.00"));

        List<AnomalyFinding> findings = detector.detect(CostDimension.SERVICE, "EC2 - Other", series,
                DateRange.of(DAY_15, LocalDate.of(2026, 9, 16)));

        assertThat(findings).hasSize(2);
        assertThat(findings.get(1).baseline()).isEqualByComparingTo("18.00");
    }

    @Test
    void severityFollowsTheAbsoluteIncrease() {
        assertThat(detector.severity(new BigDecimal("99.99"))).isEqualTo(Severity.LOW);
        assertThat(detector.severity(new BigDecimal("100.00"))).isEqualTo(Severity.MEDIUM);
        assertThat(detector.severity(new BigDecimal("500.00"))).isEqualTo(Severity.HIGH);
    }

    @Test
    void medianOfAnEvenNumberOfValuesIsTheMeanOfTheMiddleTwo() {
        assertThat(AnomalyDetector.median(List.of(new BigDecimal("1"), new BigDecimal("4"), new BigDecimal("2"),
                new BigDecimal("3")))).isEqualByComparingTo("2.5");
    }
}
