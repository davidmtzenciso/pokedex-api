package com.elatusdev.pokedex.catalog.application;

import com.elatusdev.pokedex.catalog.domain.UpstreamTimeoutException;
import com.elatusdev.pokedex.catalog.domain.UpstreamUnavailableException;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.elatusdev.pokedex.pokedex.domain.Pokemon;

// One statement of the degradation rule, because both read paths need it and two copies
// would drift. Only an OUTAGE falls back: an absent Pokemon is an answer, not a failure,
// so a 404 propagates and is never masked by stale local data.
@Component
public class UpstreamOutagePolicy {

    private static final Logger log = LoggerFactory.getLogger(UpstreamOutagePolicy.class);

    public <T> T applyTo(Supplier<T> fromUpstream, Supplier<Optional<T>> fromReplica) {
        try {
            return fromUpstream.get();
        } catch (UpstreamUnavailableException | UpstreamTimeoutException outage) {
            Optional<T> replica = fromReplica.get();
            replica.ifPresent(unused -> log.warn("serving stale local data: {}", outage.getMessage()));
            return replica.orElseThrow(() -> outage);
        }
    }
}
