package com.elatusdev.pokedex.identity.domain;


public interface PasswordHasher {

    PasswordHash hash(String rawPassword);

    boolean matches(String rawPassword, PasswordHash expected);
}
