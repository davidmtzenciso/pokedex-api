package com.elatusdev.pokedex.domain.model;

// Carried as a claim and checked on verify. Without it an access token is accepted at
// /token/refresh and a refresh token is accepted as a bearer credential — the two have very
// different lifetimes, and confusing them turns a 15-minute credential into a 7-day one.
public enum TokenType {
    ACCESS,
    REFRESH
}
