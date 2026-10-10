package com.cloudcost.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class DateRangeTest {

    private static final LocalDate SEP_1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate SEP_30 = LocalDate.of(2026, 9, 30);

    @Test
    void rangesAreInclusiveOfBothEnds() {
        DateRange september = DateRange.of(SEP_1, SEP_30);

        assertThat(september.days()).isEqualTo(30);
        assertThat(september.contains(SEP_1)).isTrue();
        assertThat(september.contains(SEP_30)).isTrue();
        assertThat(september.contains(SEP_30.plusDays(1))).isFalse();
        assertThat(september.dates()).hasSize(30);
        assertThat(DateRange.of(SEP_1, SEP_1).days()).isEqualTo(1);
    }

    @Test
    void previousPeriodHasTheSameLengthAndEndsTheDayBefore() {
        DateRange previous = DateRange.of(SEP_1, SEP_30).previousPeriod();

        assertThat(previous.start()).isEqualTo(LocalDate.of(2026, 8, 2));
        assertThat(previous.end()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(previous.days()).isEqualTo(30);
    }

    @Test
    void endingOnBuildsTrailingWindows() {
        assertThat(DateRange.endingOn(SEP_30, 30)).isEqualTo(DateRange.of(SEP_1, SEP_30));
        assertThat(DateRange.endingOn(SEP_30, 1)).isEqualTo(DateRange.of(SEP_30, SEP_30));
    }

    @Test
    void rejectsInvertedAndIncompleteRanges() {
        assertThatThrownBy(() -> DateRange.of(SEP_30, SEP_1))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("must be on or before");
        assertThatThrownBy(() -> DateRange.of(null, SEP_1)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void requestedRangesAreLimitedInLength() {
        assertThat(DateRange.ofRequest(SEP_1, SEP_1.plusDays(DateRange.MAX_REQUEST_DAYS - 1)).days())
                .isEqualTo(DateRange.MAX_REQUEST_DAYS);
        assertThatThrownBy(() -> DateRange.ofRequest(SEP_1, SEP_1.plusDays(DateRange.MAX_REQUEST_DAYS)))
                .isInstanceOf(InvalidRequestException.class);
    }
}
