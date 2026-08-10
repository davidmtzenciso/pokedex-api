package com.elatusdev.pokedex.identity.infrastructure;

import com.elatusdev.pokedex.identity.domain.PasswordHasher;
import com.elatusdev.pokedex.identity.domain.PasswordHash;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

// BCrypt at cost 12. SHA-256 is a digest and not a password hash (java:S5344): it is fast,
// which is exactly the property an offline attacker wants.
@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private static final int COST = 12;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(COST);

    @Override
    public PasswordHash hash(String rawPassword) {
        return new PasswordHash(encoder.encode(rawPassword));
    }

    @Override
    public boolean matches(String rawPassword, PasswordHash expected) {
        return encoder.matches(rawPassword, expected.value());
    }
}
