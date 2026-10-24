package com.ftcrm.time;

import java.time.Duration;
import java.time.Instant;

public final class AdjustableTimeSource implements TimeSource {

    private Instant current = Instant.parse("2026-10-23T08:00:00Z");

    public void set(Instant instant) {
        this.current = instant;
    }

    public void advance(Duration duration) {
        this.current = this.current.plus(duration);
    }

    @Override
    public Instant now() {
        return current;
    }
}
