package com.ftcrm.time;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Profile("!test")
public class SystemTimeSource implements TimeSource {

    @Override
    public Instant now() {
        return Instant.now();
    }
}
