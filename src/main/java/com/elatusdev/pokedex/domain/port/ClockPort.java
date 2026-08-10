// Copyright (c) 2026 ElatusDev
package com.elatusdev.pokedex.domain.port;

import java.time.Instant;

// every time-dependent rule is testable without sleeping, which is why Instant.now() is
// never called directly anywhere in domain or application
public interface ClockPort {

    Instant now();
}
