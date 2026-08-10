// Copyright (c) 2026 ElatusDev
package com.elatusdev.pokedex.domain.model;

import com.elatusdev.pokedex.domain.exception.InvalidPokemonDataException;
import com.elatusdev.pokedex.domain.vo.Email;
import com.elatusdev.pokedex.domain.vo.PasswordHash;
import com.elatusdev.pokedex.domain.vo.UserId;
import com.elatusdev.pokedex.domain.vo.Username;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class User {

    private final Optional<UserId> id;
    private final Username username;
    private final Email email;
    private final PasswordHash passwordHash;
    private final Set<Role> roles;
    private final Instant createdAt;

    private User(
            Optional<UserId> id,
            Username username,
            Email email,
            PasswordHash passwordHash,
            Set<Role> roles,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.username = Objects.requireNonNull(username, "username");
        this.email = Objects.requireNonNull(email, "email");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.roles = Set.copyOf(Objects.requireNonNull(roles, "roles"));
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        requireAtLeastOneRole(this.roles);
    }

    public static User register(
            Username username, Email email, PasswordHash passwordHash, Set<Role> roles, Instant createdAt) {
        return new User(Optional.empty(), username, email, passwordHash, roles, createdAt);
    }

    public static User rehydrate(
            UserId id,
            Username username,
            Email email,
            PasswordHash passwordHash,
            Set<Role> roles,
            Instant createdAt) {
        return new User(Optional.of(id), username, email, passwordHash, roles, createdAt);
    }

    public Optional<UserId> id() {
        return id;
    }

    public Username username() {
        return username;
    }

    public Email email() {
        return email;
    }

    public PasswordHash passwordHash() {
        return passwordHash;
    }

    public Set<Role> roles() {
        return roles;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    // I10 — the hash must not reach a log line, and toString is how it usually would
    @Override
    public String toString() {
        return "User[username=" + username.value() + ", roles=" + roles + "]";
    }

    private static void requireAtLeastOneRole(Set<Role> roles) {
        if (roles.isEmpty()) {
            throw new InvalidPokemonDataException("a user must hold at least one role");
        }
    }
}
