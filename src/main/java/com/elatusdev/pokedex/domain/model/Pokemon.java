// Copyright (c) 2026 ElatusDev
package com.elatusdev.pokedex.domain.model;

import com.elatusdev.pokedex.domain.exception.InvalidPokemonDataException;
import com.elatusdev.pokedex.domain.vo.PokeApiId;
import com.elatusdev.pokedex.domain.vo.PokemonId;
import com.elatusdev.pokedex.domain.vo.Region;
import com.elatusdev.pokedex.domain.vo.Notes;
import com.elatusdev.pokedex.domain.vo.Tag;
import com.elatusdev.pokedex.domain.vo.UserId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class Pokemon {

    private final Optional<PokemonId> id;
    private Optional<PokeApiId> pokeApiId;
    private ReplicatedFields replicated;
    private ProprietaryFields proprietary;
    private ReplicationState replicationState;
    private Optional<Instant> syncedAt;
    private long version;

    private Pokemon(
            Optional<PokemonId> id,
            Optional<PokeApiId> pokeApiId,
            ReplicatedFields replicated,
            ProprietaryFields proprietary,
            ReplicationState replicationState,
            Optional<Instant> syncedAt,
            long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.pokeApiId = Objects.requireNonNull(pokeApiId, "pokeApiId");
        this.replicated = Objects.requireNonNull(replicated, "replicated");
        this.proprietary = Objects.requireNonNull(proprietary, "proprietary");
        this.replicationState = Objects.requireNonNull(replicationState, "replicationState");
        this.syncedAt = Objects.requireNonNull(syncedAt, "syncedAt");
        this.version = version;
        requireStateConsistency();
    }

    public static Pokemon draft(ReplicatedFields replicated) {
        return new Pokemon(
                Optional.empty(),
                Optional.empty(),
                replicated,
                ProprietaryFields.none(),
                ReplicationState.DRAFT,
                Optional.empty(),
                0L);
    }

    public static Pokemon pending(PokeApiId pokeApiId, ReplicatedFields replicated) {
        return new Pokemon(
                Optional.empty(),
                Optional.of(pokeApiId),
                replicated,
                ProprietaryFields.none(),
                ReplicationState.PENDING,
                Optional.empty(),
                0L);
    }

    public static Pokemon rehydrate(
            PokemonId id,
            Optional<PokeApiId> pokeApiId,
            ReplicatedFields replicated,
            ProprietaryFields proprietary,
            ReplicationState replicationState,
            Optional<Instant> syncedAt,
            long version) {
        return new Pokemon(
                Optional.of(id), pokeApiId, replicated, proprietary, replicationState, syncedAt, version);
    }

    public void addTag(Tag tag) {
        List<Tag> tags = new ArrayList<>(proprietary.tags());
        requireTagIsAddable(tag, tags);
        tags.add(tag);
        proprietary = proprietary.withTags(tags);
    }

    public void removeTag(Tag tag) {
        List<Tag> tags = new ArrayList<>(proprietary.tags());
        tags.remove(tag);
        proprietary = proprietary.withTags(tags);
    }

    public void curateBy(UserId curator) {
        proprietary = proprietary.withCurator(curator);
    }

    public void assignRegion(Region region) {
        proprietary = proprietary.withRegion(region);
    }

    public void annotate(Notes notes) {
        proprietary = proprietary.withNotes(notes);
    }

    public void linkToUpstream(PokeApiId upstreamId) {
        replicationState = replicationState.transitionTo(ReplicationState.PENDING);
        pokeApiId = Optional.of(upstreamId);
        requireStateConsistency();
    }

    public void transitionTo(ReplicationState next, Instant at) {
        ReplicationState previous = replicationState;
        Optional<Instant> previousSyncedAt = syncedAt;
        replicationState = replicationState.transitionTo(next);
        syncedAt = next.isReplicated() ? Optional.of(at) : syncedAt;
        restoreIfInconsistent(previous, previousSyncedAt);
    }

    // exactly one call site writes replicated fields from upstream: the STALE -> {SYNCED,
    // CUSTOMIZED} edge, driven by PokemonMergePolicy in WU-US03-B
    public void replaceReplicated(ReplicatedFields upstream, Instant at) {
        replicated = Objects.requireNonNull(upstream, "upstream");
        transitionTo(proprietary.isEmpty() ? ReplicationState.SYNCED : ReplicationState.CUSTOMIZED, at);
    }

    public Optional<PokemonId> id() {
        return id;
    }

    public Optional<PokeApiId> pokeApiId() {
        return pokeApiId;
    }

    public ReplicatedFields replicated() {
        return replicated;
    }

    public ProprietaryFields proprietary() {
        return proprietary;
    }

    public List<Tag> tags() {
        return proprietary.tags();
    }

    public Optional<UserId> curatedBy() {
        return proprietary.curatedBy();
    }

    public ReplicationState replicationState() {
        return replicationState;
    }

    public Optional<Instant> syncedAt() {
        return syncedAt;
    }

    public long version() {
        return version;
    }

    private void requireTagIsAddable(Tag tag, List<Tag> current) {
        requireTagCapacity(current);
        requireTagIsDistinct(tag, current);
    }

    private void requireTagCapacity(List<Tag> current) {
        if (current.size() >= ProprietaryFields.MAX_TAGS) {
            throw new InvalidPokemonDataException(
                    "a Pokemon carries at most " + ProprietaryFields.MAX_TAGS + " tags");
        }
    }

    // Tag equality is already case-insensitive, so contains carries the I4 distinctness rule
    private void requireTagIsDistinct(Tag tag, List<Tag> current) {
        if (current.contains(tag)) {
            throw new InvalidPokemonDataException("this Pokemon already carries the tag '" + tag.label() + "'");
        }
    }

    private void restoreIfInconsistent(ReplicationState previous, Optional<Instant> previousSyncedAt) {
        try {
            requireStateConsistency();
        } catch (InvalidPokemonDataException rejected) {
            replicationState = previous;
            syncedAt = previousSyncedAt;
            throw rejected;
        }
    }

    private void requireStateConsistency() {
        requireDraftExactlyWhenUnlinked();
        requireSyncTimestampWhenReplicated();
    }

    // F6 — DRAFT is exactly the set of unlinked records, in both directions
    private void requireDraftExactlyWhenUnlinked() {
        if ((replicationState == ReplicationState.DRAFT) == pokeApiId.isPresent()) {
            throw new InvalidPokemonDataException(
                    "DRAFT is exactly the set of records with no pokeApiId, was " + replicationState);
        }
    }

    // F5 — any replicated state implies a sync timestamp
    private void requireSyncTimestampWhenReplicated() {
        if (replicationState.isReplicated() && syncedAt.isEmpty()) {
            throw new InvalidPokemonDataException("a replicated state requires syncedAt, was " + replicationState);
        }
    }
}
