package com.elatusdev.pokedex.identity.interfaces;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

// user_id is a plain column, not a @ManyToOne: RefreshToken references its owner by id, and
// an association would let a token drag a User graph into every rotation.
//
// created_at is deliberately unmapped. The column is NOT NULL DEFAULT now() and is not
// domain state — RefreshToken has no such field — so the database owns it.
@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenDataModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "family_id", nullable = false, length = 64, updatable = false)
    private String familyId;

    @Column(name = "jti", nullable = false, length = 64, updatable = false)
    private String jti;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    // the only mutable column: a token is issued once and revoked at most once
    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected RefreshTokenDataModel() {
    }

    public RefreshTokenDataModel(
            Long id, Long userId, String familyId, String jti, Instant expiresAt, Instant revokedAt) {
        this.id = id;
        this.userId = userId;
        this.familyId = familyId;
        this.jti = jti;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getFamilyId() {
        return familyId;
    }

    public String getJti() {
        return jti;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
