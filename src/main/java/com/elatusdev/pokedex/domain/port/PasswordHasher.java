// Copyright (c) 2026 ElatusDev
package com.elatusdev.pokedex.domain.port;

import com.elatusdev.pokedex.domain.vo.PasswordHash;

public interface PasswordHasher {

    PasswordHash hash(String rawPassword);

    boolean matches(String rawPassword, PasswordHash expected);
}
