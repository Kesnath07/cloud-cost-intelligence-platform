package com.cloudcost.web.chart;

import java.util.List;

/**
 * Geometry of a server-rendered SVG column chart. Templates only place the precomputed
 * shapes, so charts need no JavaScript and work under the strict content security policy.
 */
public record ColumnChart(
        int width,
        int height,
        double plotLeft,
        double plotRight,
        double zeroY,
        List<Column> columns,
        List<Tick> ticks,
        List<Label> labels,
        boolean empty) {

    /**
     * One data point. {@code path} is empty for points without data, which are drawn as a
     * small gap marker instead of a zero-height column.
     */
    public record Column(String path, double hitX, double hitWidth, double markerX, String title, boolean hasData) {
    }

    /** A horizontal gridline with its axis label. */
    public record Tick(double y, String label) {
    }

    /** An x-axis category label. */
    public record Label(double x, String text) {
    }
}
