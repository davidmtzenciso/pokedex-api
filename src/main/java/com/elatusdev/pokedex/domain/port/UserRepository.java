// Copyright (c) 2026 ElatusDev
package com.elatusdev.pokedex.domain.port;

import com.elatusdev.pokedex.domain.model.User;
import com.elatusdev.pokedex.domain.vo.Email;
import com.elatusdev.pokedex.domain.vo.UserId;
import com.elatusdev.pokedex.domain.vo.Username;
import java.util.Optional;

public interface UserRepository {

    Optional<User> findById(UserId id);

    Optional<User> findByUsername(Username username);

    boolean existsByUsername(Username username);

    boolean existsByEmail(Email email);

    User save(User user);
}
