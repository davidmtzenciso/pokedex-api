package com.elatusdev.pokedex.pokedex.domain;

// Replication could not reach upstream, said in pokedex's own words.
//
// catalog already has UpstreamUnavailableException and UpstreamTimeoutException, and pokedex
// cannot name either without closing the CY1 cycle. PokedexUpstreamCatalogAdapter translates
// them into this on the way across — which is what an anti-corruption boundary is for, and
// the reason batch can tell "upstream is down" from "there was nothing to do" without
// catching RuntimeException and hoping.
//
// The contract lists 502 and not 504 for sync, so both upstream failure modes land here.
public class UpstreamReplicationFailedException extends RuntimeException {

    private final transient String idOrName;

    public UpstreamReplicationFailedException(String idOrName, Throwable cause) {
        super("Upstream replication failed for '" + idOrName + "': " + cause.getMessage(), cause);
        this.idOrName = idOrName;
    }

    public String idOrName() {
        return idOrName;
    }
}
