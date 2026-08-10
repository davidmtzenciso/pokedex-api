package com.elatusdev.pokedex.identity.infrastructure;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.elatusdev.pokedex.identity.domain.Username;
import com.elatusdev.pokedex.identity.domain.Email;

// Username and Email normalise to lower case in their constructors, so the stored values are
// already normalised and plain equality is the case-insensitive comparison.
interface UserJpaRepository extends JpaRepository<UserDataModel, Long> {

    Optional<UserDataModel> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
