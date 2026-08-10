package com.elatusdev.pokedex.infrastructure.time;

import com.elatusdev.pokedex.domain.port.ClockPort;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

// The one place Instant.now() would otherwise appear. Reading through an explicit UTC Clock
// keeps every time-dependent rule testable without sleeping, and keeps the service's notion
// of "now" independent of the host's default zone.
@Component
public class SystemClockAdapter implements ClockPort {

    private final Clock clock = Clock.systemUTC();

    @Override
    public Instant now() {
        return clock.instant();
    }
}
