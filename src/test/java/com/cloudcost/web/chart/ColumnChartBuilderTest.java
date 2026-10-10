package com.cloudcost.web.chart;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.cloudcost.web.chart.ColumnChartBuilder.Point;

import org.junit.jupiter.api.Test;

class ColumnChartBuilderTest {

    private static ColumnChart chart(String... values) {
        List<Point> points = Arrays.stream(values)
                .map(v -> new Point("x", v == null ? null : new BigDecimal(v), "t"))
                .toList();
        return ColumnChartBuilder.build(points, p -> p, v -> String.valueOf(v.intValue()));
    }

    @Test
    void usesNiceTickStepsStartingAtZero() {
        ColumnChart chart = chart("120", "340", "455");

        assertThat(chart.ticks()).extracting(ColumnChart.Tick::label).containsExactly("0", "200", "400", "600");
        assertThat(ColumnChartBuilder.niceStep(455)).isEqualTo(200);
        assertThat(ColumnChartBuilder.niceStep(9)).isEqualTo(2.5);
    }

    @Test
    void capsColumnWidthAndMarksGapsInsteadOfDrawingZeroColumns() {
        ColumnChart chart = chart("10", null, "0", "5");

        assertThat(chart.columns()).extracting(ColumnChart.Column::hasData).containsExactly(true, false, true, true);
        assertThat(chart.columns().get(1).path()).isEmpty();
        assertThat(chart.columns().get(2).path()).as("zero has no visible column").isEmpty();
        assertThat(chart.empty()).isFalse();
    }

    @Test
    void negativeValuesExtendBelowTheBaseline() {
        ColumnChart chart = chart("100", "-40");

        assertThat(chart.ticks().get(0).label()).isEqualTo("-50");
        String negative = chart.columns().get(1).path();
        double baseline = chart.zeroY();
        assertThat(negative).startsWith(String.format(Locale.ROOT, "M%.1f %.1f",
                chart.columns().get(1).markerX() - 12, baseline));
    }

    @Test
    void emptyInputProducesAnEmptyChart() {
        assertThat(ColumnChartBuilder.build(List.<Point>of(), p -> p, String::valueOf).empty()).isTrue();
        assertThat(chart(null, null).empty()).isTrue();
    }
}
