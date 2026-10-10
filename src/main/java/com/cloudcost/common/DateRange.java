package com.cloudcost.common;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

/**
 * An inclusive range of UTC calendar days. Both ends are part of the range, so a range
 * from 2026-09-01 to 2026-09-30 covers 30 days.
 */
public record DateRange(LocalDate start, LocalDate end) {

    /** Upper bound for ranges requested by users, to keep queries and charts bounded. */
    public static final int MAX_REQUEST_DAYS = 1096;

    public DateRange {
        if (start == null || end == null) {
            throw new InvalidRequestException("Both a start date and an end date are required");
        }
        if (end.isBefore(start)) {
            throw new InvalidRequestException(
                    "The start date (" + start + ") must be on or before the end date (" + end + ")");
        }
    }

    public static DateRange of(LocalDate start, LocalDate end) {
        return new DateRange(start, end);
    }

    /** Creates a range from user input, additionally enforcing {@link #MAX_REQUEST_DAYS}. */
    public static DateRange ofRequest(LocalDate start, LocalDate end) {
        DateRange range = new DateRange(start, end);
        if (range.days() > MAX_REQUEST_DAYS) {
            throw new InvalidRequestException("Date ranges are limited to " + MAX_REQUEST_DAYS + " days");
        }
        return range;
    }

    /** The {@code days}-day range that ends on {@code end}. */
    public static DateRange endingOn(LocalDate end, int days) {
        if (days < 1) {
            throw new IllegalArgumentException("days must be positive");
        }
        return new DateRange(end.minusDays(days - 1L), end);
    }

    public long days() {
        return ChronoUnit.DAYS.between(start, end) + 1;
    }

    /** The range of equal length immediately before this one. */
    public DateRange previousPeriod() {
        long days = days();
        return new DateRange(start.minusDays(days), start.minusDays(1));
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }

    public Stream<LocalDate> dates() {
        return start.datesUntil(end.plusDays(1));
    }

    @Override
    public String toString() {
        return start + " to " + end;
    }
}
