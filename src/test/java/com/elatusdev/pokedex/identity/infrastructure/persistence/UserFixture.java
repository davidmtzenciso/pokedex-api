package com.elatusdev.pokedex.identity.infrastructure.persistence;

import com.elatusdev.pokedex.identity.domain.model.Role;
import com.elatusdev.pokedex.identity.domain.model.User;
import com.elatusdev.pokedex.identity.domain.vo.Email;
import com.elatusdev.pokedex.identity.domain.vo.PasswordHash;
import com.elatusdev.pokedex.identity.domain.vo.Username;
import java.time.Instant;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;

// identity's own fixture. Borrowing the pokedex one would make the auxiliary API depend on
// the collection it is meant to know nothing about (BC1).
final class UserFixture {

    private UserFixture() {}

    static void clear(JdbcTemplate jdbc) {
        jdbc.execute("TRUNCATE TABLE pokemon, users RESTART IDENTITY CASCADE");
    }

    static User curator(String username) {
        return User.register(
                new Username(username),
                new Email(username + "@example.com"),
                new PasswordHash("$2a$10$abcdefghijklmnopqrstuv"),
                Set.of(Role.CURATOR),
                Instant.parse("2026-08-01T09:00:00Z"));
    }
}
