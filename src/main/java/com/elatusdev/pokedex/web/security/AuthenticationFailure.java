package com.elatusdev.pokedex.web.security;

// The §9.5 rows that a filter can produce. All four are 401: a 403 means the principal is
// known and lacks the role, never that a credential was bad.
public enum AuthenticationFailure {
    UNAUTHENTICATED("No bearer token was presented"),
    INVALID_TOKEN("The bearer token could not be verified"),
    TOKEN_REVOKED("The session behind this token has been closed"),
    SESSION_STORE_UNAVAILABLE("The session could not be confirmed");

    private final String detail;

    AuthenticationFailure(String detail) {
        this.detail = detail;
    }

    public String detail() {
        return detail;
    }

    // an outage is not a distinct answer to the caller: it is reported as a revoked session
    // so that a Redis outage cannot be probed from outside
    public String code() {
        return this == SESSION_STORE_UNAVAILABLE ? TOKEN_REVOKED.name() : name();
    }
}
