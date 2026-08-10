package com.elatusdev.pokedex.identity.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdentityExceptionTest {

    @Test
    void should_carry_the_family_when_a_rotated_refresh_token_is_replayed() {
        TokenReuseDetectedException thrown = new TokenReuseDetectedException("family-7");

        assertThat(thrown.familyId()).isEqualTo("family-7");
        assertThat(thrown).hasMessage("Refresh token reuse detected; family revoked");
    }

    // the family id identifies the revocation target; it is not a secret and not the token
    @Test
    void should_not_echo_the_replayed_token_in_the_message() {
        TokenReuseDetectedException thrown = new TokenReuseDetectedException("family-7");

        assertThat(thrown.getMessage()).doesNotContain("family-7");
    }

    @Test
    void should_carry_the_conflicting_field_when_a_user_already_exists() {
        UserAlreadyExistsException thrown = new UserAlreadyExistsException("username");

        assertThat(thrown.field()).isEqualTo("username");
        assertThat(thrown).hasMessage("A user with that username already exists");
    }
}
