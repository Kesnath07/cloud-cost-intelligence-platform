package com.cloudcost.web;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

import com.cloudcost.analysis.DailyCost;
import com.cloudcost.analysis.MonthlyCost;
import com.cloudcost.config.PlatformProperties;
import com.cloudcost.web.chart.ColumnChart;
import com.cloudcost.web.chart.ColumnChartBuilder;
import com.cloudcost.web.chart.ColumnChartBuilder.Point;

import org.springframework.stereotype.Component;

/** Builds the dashboard's daily and monthly column charts with consistent labels and tooltips. */
@Component
public class ChartFactory {

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MMM d", Locale.US);
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US);

    private final ViewFormatter formatter;
    private final Currency currency;

    public ChartFactory(ViewFormatter formatter, PlatformProperties properties) {
        this.formatter = formatter;
        this.currency = Currency.getInstance(properties.reportingCurrency());
    }

    public ColumnChart daily(List<DailyCost> days) {
        return ColumnChartBuilder.build(days, day -> new Point(DAY_LABEL.format(day.date()), day.amount(),
                day.date() + ": " + (day.hasData() ? formatter.money(day.amount()) : "no data")), this::axisLabel);
    }

    public ColumnChart monthly(List<MonthlyCost> months) {
        return ColumnChartBuilder.build(months, month -> new Point(MONTH_LABEL.format(month.month()),
                month.daysWithData() == 0 ? null : month.amount(),
                MONTH_LABEL.format(month.month()) + ": " + (month.daysWithData() == 0 ? "no data"
                        : formatter.money(month.amount()) + (month.partial() ? " (partial month)" : "")
                                + ", " + month.daysWithData() + " day(s) with data")),
                this::axisLabel);
    }

    private String axisLabel(double value) {
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.US);
        format.setCurrency(currency);
        boolean whole = Math.abs(value - Math.rint(value)) < 1e-9;
        format.setMaximumFractionDigits(whole ? 0 : 2);
        format.setMinimumFractionDigits(whole ? 0 : 2);
        return format.format(BigDecimal.valueOf(value));
    }
}
