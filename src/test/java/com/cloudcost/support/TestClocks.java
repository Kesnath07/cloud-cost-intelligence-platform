package com.cloudcost.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

public final class TestClocks {

    /** A fixed "now" shortly after the demonstration dataset ends. */
    public static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");

    private TestClocks() {
    }

    public static Clock fixed() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }
}
