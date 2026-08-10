package com.elatusdev.pokedex.infrastructure.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.UpdateTimestamp;

// refresh_tokens is a table in V1 but not an association here: RefreshToken is WU-AUTH-A's
// aggregate child, and a collection this class does not need would load on every login.
@Entity
@Table(name = "users")
public class UserDataModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, length = 30)
    private String username;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    // the ERD models roles as one column; a join table for a two-member closed enum buys
    // nothing a comma-joined list does not
    @Column(name = "roles", nullable = false, length = 100)
    private String roles;

    // unlike pokemon.created_at this one is domain state — User.createdAt — so the mapper
    // supplies it, and no update may rewrite it
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserDataModel() {
    }

    public UserDataModel(
            Long id, String username, String email, String passwordHash, String roles, Instant createdAt) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.roles = roles;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getRoles() {
        return roles;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
