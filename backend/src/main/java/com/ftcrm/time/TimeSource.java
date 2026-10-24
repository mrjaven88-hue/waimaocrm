package com.ftcrm.time;

import java.time.Instant;

public interface TimeSource {

    Instant now();
}
