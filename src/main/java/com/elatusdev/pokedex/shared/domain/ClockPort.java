package com.elatusdev.pokedex.shared.domain;

import java.time.Instant;

// every time-dependent rule is testable without sleeping, which is why Instant.now() is
// never called directly anywhere in domain or application
public interface ClockPort {

    Instant now();
}
