package com.elatusdev.pokedex.pokedex.domain.model;

import com.elatusdev.pokedex.pokedex.domain.exception.IllegalStateTransitionException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum ReplicationState {
    DRAFT, PENDING, SYNCED, CUSTOMIZED, STALE, FAILED;

    private static final Map<ReplicationState, Set<ReplicationState>> LEGAL = Map.of(
            DRAFT, EnumSet.of(PENDING, DRAFT),
            PENDING, EnumSet.of(SYNCED, FAILED),
            SYNCED, EnumSet.of(CUSTOMIZED, STALE),
            CUSTOMIZED, EnumSet.of(CUSTOMIZED, STALE),
            STALE, EnumSet.of(SYNCED, CUSTOMIZED, FAILED),
            FAILED, EnumSet.of(PENDING));

    public boolean canTransitionTo(ReplicationState next) {
        return LEGAL.get(this).contains(next);
    }

    public ReplicationState transitionTo(ReplicationState next) {
        if (!canTransitionTo(next)) {
            throw new IllegalStateTransitionException(this, next);
        }
        return next;
    }

    public boolean isReplicated() {
        return this == SYNCED || this == CUSTOMIZED || this == STALE;
    }
}
