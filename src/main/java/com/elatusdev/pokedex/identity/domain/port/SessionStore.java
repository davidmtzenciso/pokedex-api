package com.elatusdev.pokedex.identity.domain.port;

import com.elatusdev.pokedex.identity.domain.vo.UserId;
import java.time.Instant;
import com.elatusdev.pokedex.shared.port.CachePort;

// What makes logout real: a token with a perfect signature whose session has been closed is
// a 401. Implementations read FAIL CLOSED — an unreachable store denies, it never grants.
// This is the deliberate opposite of CachePort, which fails open.
public interface SessionStore {

    void open(String jti, UserId subject, Instant expiresAt);

    boolean isLive(String jti);

    void close(String jti);
}
