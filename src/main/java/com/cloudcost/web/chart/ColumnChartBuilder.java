package com.cloudcost.web.chart;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Builds {@link ColumnChart} geometry: columns at most 24px wide with a 2px gap, a 4px
 * rounded data end and a square baseline end, a zero baseline (negative values such as net
 * credits extend below it) and "nice" y-axis ticks.
 */
public final class ColumnChartBuilder {

    public static final int WIDTH = 720;
    public static final int HEIGHT = 220;
    private static final double LEFT = 64;
    private static final double RIGHT = 8;
    private static final double TOP = 12;
    private static final double BOTTOM = 26;
    private static final double MAX_BAR = 24;
    private static final double GAP = 2;
    private static final double RADIUS = 4;
    private static final int TARGET_TICKS = 4;
    private static final int MAX_LABELS = 7;

    /** A value to plot; {@code value == null} marks a gap (no data). */
    public record Point(String label, BigDecimal value, String title) {
    }

    private ColumnChartBuilder() {
    }

    public static <T> ColumnChart build(List<T> items, Function<T, Point> toPoint, Function<Double, String> tickFormat) {
        List<Point> points = items.stream().map(toPoint).toList();
        double plotWidth = WIDTH - LEFT - RIGHT;
        double plotHeight = HEIGHT - TOP - BOTTOM;
        if (points.isEmpty()) {
            return new ColumnChart(WIDTH, HEIGHT, LEFT, WIDTH - RIGHT, TOP + plotHeight, List.of(), List.of(),
                    List.of(), true);
        }

        double max = 0;
        double min = 0;
        boolean anyData = false;
        for (Point point : points) {
            if (point.value() != null) {
                anyData = true;
                max = Math.max(max, point.value().doubleValue());
                min = Math.min(min, point.value().doubleValue());
            }
        }
        double step = niceStep(Math.max(max - min, 1e-9));
        double top = max <= 0 ? step : Math.ceil(max / step) * step;
        double bottom = min >= 0 ? 0 : Math.floor(min / step) * step;
        double scale = plotHeight / (top - bottom);
        double zeroY = TOP + (top * scale);

        List<ColumnChart.Tick> ticks = new ArrayList<>();
        for (double value = bottom; value <= top + step / 2; value += step) {
            ticks.add(new ColumnChart.Tick(round(TOP + (top - value) * scale), tickFormat.apply(value)));
        }

        double slot = plotWidth / points.size();
        double barWidth = Math.max(1, Math.min(MAX_BAR, slot - GAP));
        List<ColumnChart.Column> columns = new ArrayList<>();
        List<ColumnChart.Label> labels = new ArrayList<>();
        int labelEvery = (int) Math.ceil(points.size() / (double) MAX_LABELS);
        for (int i = 0; i < points.size(); i++) {
            Point point = points.get(i);
            double slotX = LEFT + i * slot;
            double x = slotX + (slot - barWidth) / 2;
            String path = "";
            if (point.value() != null && point.value().signum() != 0) {
                double y = TOP + (top - point.value().doubleValue()) * scale;
                path = columnPath(x, barWidth, zeroY, y);
            }
            columns.add(new ColumnChart.Column(path, round(slotX), round(slot), round(x + barWidth / 2),
                    point.title(), point.value() != null));
            if (i % labelEvery == 0) {
                labels.add(new ColumnChart.Label(round(slotX + slot / 2), point.label()));
            }
        }
        return new ColumnChart(WIDTH, HEIGHT, LEFT, WIDTH - RIGHT, round(zeroY), columns, ticks, labels, !anyData);
    }

    /** A column from the baseline to {@code y}, rounded only at its data end. */
    static String columnPath(double x, double width, double baseY, double y) {
        double height = Math.abs(baseY - y);
        double r = Math.min(RADIUS, Math.min(width / 2, height));
        double right = x + width;
        if (y < baseY) {
            return String.format(Locale.ROOT, "M%.1f %.1fV%.1fQ%.1f %.1f %.1f %.1fH%.1fQ%.1f %.1f %.1f %.1fV%.1fZ",
                    x, baseY, y + r, x, y, x + r, y, right - r, right, y, right, y + r, baseY);
        }
        return String.format(Locale.ROOT, "M%.1f %.1fV%.1fQ%.1f %.1f %.1f %.1fH%.1fQ%.1f %.1f %.1f %.1fV%.1fZ",
                x, baseY, y - r, x, y, x + r, y, right - r, right, y, right, y - r, baseY);
    }

    /** A step of 1, 2, 2.5 or 5 times a power of ten giving about {@value #TARGET_TICKS} intervals. */
    static double niceStep(double span) {
        double raw = span / TARGET_TICKS;
        double magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
        double normalized = raw / magnitude;
        double nice = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 2.5 ? 2.5 : normalized <= 5 ? 5 : 10;
        return nice * magnitude;
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
