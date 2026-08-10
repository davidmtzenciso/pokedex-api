package com.elatusdev.pokedex.identity.domain.port;

import com.elatusdev.pokedex.identity.domain.vo.PasswordHash;

public interface PasswordHasher {

    PasswordHash hash(String rawPassword);

    boolean matches(String rawPassword, PasswordHash expected);
}
