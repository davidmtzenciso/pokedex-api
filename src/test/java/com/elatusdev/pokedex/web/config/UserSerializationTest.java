// Copyright (c) 2026 ElatusDev
package com.elatusdev.pokedex.web.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.elatusdev.pokedex.domain.model.Role;
import com.elatusdev.pokedex.domain.model.User;
import com.elatusdev.pokedex.domain.vo.Email;
import com.elatusdev.pokedex.domain.vo.PasswordHash;
import com.elatusdev.pokedex.domain.vo.UserId;
import com.elatusdev.pokedex.domain.vo.Username;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

// I10 — a password hash is never stored in clear, logged, or returned. toString is covered
// by UserTest and ValueObjectTest; this is the serialisation half, which is the path that
// actually reaches a client.
class UserSerializationTest {

    private static final String RAW_HASH = "$2a$12$7EqJtq98hPqEX7fNZaFWoO2Bl0z3Yr5xKvL1mNpQrStUvWxYzAbCd";
    private static final PasswordHash HASH = new PasswordHash(RAW_HASH);

    private final ObjectMapper mapper =
            JsonMapper.builder().addModule(new CredentialMaskingModule()).build();

    @Test
    void should_mask_the_hash_when_the_value_object_is_serialised() {
        assertThat(mapper.writeValueAsString(HASH)).isEqualTo("\"***\"").doesNotContain(RAW_HASH);
    }

    @Test
    void should_mask_the_hash_when_it_is_nested_in_an_object_graph() {
        String json = mapper.writeValueAsString(Map.of("credential", HASH));

        assertThat(json).isEqualTo("{\"credential\":\"***\"}").doesNotContain(RAW_HASH);
    }

    @Test
    void should_mask_the_hash_when_it_appears_in_a_collection() {
        String json = mapper.writeValueAsString(List.of(HASH, new PasswordHash("$2a$12$second")));

        assertThat(json).isEqualTo("[\"***\",\"***\"]").doesNotContain("$2a$12$");
    }

    // The aggregate exposes no bean accessors, so it serialises to nothing at all — the wire
    // shape is a DTO. Asserting the exact empty document rather than "does not contain the
    // hash" is what makes this falsifiable: adding a getPasswordHash() turns it red, and a
    // doesNotContain assertion would still pass while leaking the username and the roles.
    @Test
    void should_serialise_the_aggregate_to_nothing_because_the_wire_shape_is_a_dto() {
        User user = User.rehydrate(
                UserId.of(1),
                new Username("demo"),
                new Email("demo@elatus-dev.com"),
                HASH,
                Set.of(Role.CURATOR),
                Instant.parse("2026-08-09T14:22:31Z"));

        assertThat(mapper.writeValueAsString(user)).isEqualTo("{}").doesNotContain(RAW_HASH);
    }
}
