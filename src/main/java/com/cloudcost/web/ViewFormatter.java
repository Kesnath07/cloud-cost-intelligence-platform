package com.cloudcost.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.Locale;

import com.cloudcost.analysis.CostFilter;
import com.cloudcost.config.PlatformProperties;

import org.springframework.stereotype.Component;

/**
 * Formatting helpers exposed to Thymeleaf templates as {@code @fmt}. Keeping formatting in
 * one place guarantees that every page renders money, percentages and timestamps the same way.
 */
@Component("fmt")
public class ViewFormatter {

    private static final String EMPTY = "—";
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final Currency currency;

    public ViewFormatter(PlatformProperties properties) {
        this.currency = Currency.getInstance(properties.reportingCurrency());
    }

    public String currencyCode() {
        return currency.getCurrencyCode();
    }

    public String money(BigDecimal amount) {
        if (amount == null) {
            return EMPTY;
        }
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.US);
        format.setCurrency(currency);
        return format.format(amount.setScale(2, RoundingMode.HALF_UP));
    }

    public String signedMoney(BigDecimal amount) {
        if (amount == null) {
            return EMPTY;
        }
        return amount.signum() > 0 ? "+" + money(amount) : money(amount);
    }

    public String percent(BigDecimal value) {
        if (value == null) {
            return "n/a";
        }
        return value.setScale(1, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    public String signedPercent(BigDecimal value) {
        if (value == null) {
            return "n/a";
        }
        return value.signum() > 0 ? "+" + percent(value) : percent(value);
    }

    public String timestamp(Instant instant) {
        return instant == null ? EMPTY : TIMESTAMP.format(instant);
    }

    public String environment(String environment) {
        return environment == null || environment.isEmpty() ? CostFilter.UNTAGGED_LABEL : environment;
    }

    public String orDash(String value) {
        return value == null || value.isBlank() ? EMPTY : value;
    }

    /** A percentage clamped to 0..100 for drawing bars; {@code null} is drawn as an empty bar. */
    public String barWidth(BigDecimal percent) {
        if (percent == null || percent.signum() <= 0) {
            return "0";
        }
        BigDecimal clamped = percent.min(BigDecimal.valueOf(100));
        return clamped.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** CSS modifier for a signed change: increases in cost are highlighted as negative news. */
    public String changeClass(BigDecimal change) {
        if (change == null || change.signum() == 0) {
            return "change-flat";
        }
        return change.signum() > 0 ? "change-up" : "change-down";
    }
}
