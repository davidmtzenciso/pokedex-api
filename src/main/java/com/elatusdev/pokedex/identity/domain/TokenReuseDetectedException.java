package com.elatusdev.pokedex.identity.domain;

public class TokenReuseDetectedException extends RuntimeException {

    private final transient String familyId;

    public TokenReuseDetectedException(String familyId) {
        super("Refresh token reuse detected; family revoked");
        this.familyId = familyId;
    }

    public String familyId() {
        return familyId;
    }
}
