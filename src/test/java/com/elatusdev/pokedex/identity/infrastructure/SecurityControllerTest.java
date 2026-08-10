package com.elatusdev.pokedex.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.elatusdev.pokedex.identity.application.AuthenticateUserUseCase;
import com.elatusdev.pokedex.identity.application.GetCurrentUserUseCase;
import com.elatusdev.pokedex.identity.application.LogoutUseCase;
import com.elatusdev.pokedex.identity.application.RefreshTokenRotationUseCase;
import com.elatusdev.pokedex.identity.application.RegisterUserUseCase;
import com.elatusdev.pokedex.identity.application.TokenPair;
import com.elatusdev.pokedex.identity.domain.Role;
import com.elatusdev.pokedex.identity.domain.TokenType;
import com.elatusdev.pokedex.identity.domain.User;
import com.elatusdev.pokedex.identity.domain.VerifiedToken;
import com.elatusdev.pokedex.identity.domain.Email;
import com.elatusdev.pokedex.identity.domain.PasswordHash;
import com.elatusdev.pokedex.identity.domain.UserId;
import com.elatusdev.pokedex.identity.domain.Username;
import com.elatusdev.pokedex.contract.dto.CurrentUserDTO;
import com.elatusdev.pokedex.contract.dto.LoginRequestDTO;
import com.elatusdev.pokedex.contract.dto.RefreshRequestDTO;
import com.elatusdev.pokedex.contract.dto.RegisterRequestDTO;
import com.elatusdev.pokedex.contract.dto.TokenPairDTO;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class SecurityControllerTest {

    private static final Instant CREATED = Instant.parse("2026-08-09T14:22:31Z");
    private static final UserId ID = UserId.of(7);
    private static final String JTI = "jti-1";

    private static final User DEMO = User.rehydrate(
            ID,
            new Username("demo"),
            new Email("demo@elatus-dev.com"),
            new PasswordHash("$2a$12$stored"),
            Set.of(Role.ADMIN, Role.CURATOR),
            CREATED);

    @Mock
    private RegisterUserUseCase registerUser;

    @Mock
    private AuthenticateUserUseCase authenticateUser;

    @Mock
    private RefreshTokenRotationUseCase rotateRefreshToken;

    @Mock
    private LogoutUseCase logoutUser;

    @Mock
    private GetCurrentUserUseCase getCurrentUser;

    private SecurityController controller() {
        return new SecurityController(
                registerUser, authenticateUser, rotateRefreshToken, logoutUser, getCurrentUser);
    }

    private static void authenticateAs(UserId subject, String jti) {
        VerifiedToken principal = new VerifiedToken(
                TokenType.ACCESS, subject, jti, Set.of(Role.CURATOR), Optional.empty(), CREATED);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, Set.of()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_return_201_and_the_principal_when_a_user_registers() {
        when(registerUser.register("demo", "demo@elatus-dev.com", "Demo123!secret")).thenReturn(DEMO);

        ResponseEntity<CurrentUserDTO> response =
                controller().register(new RegisterRequestDTO("demo", "demo@elatus-dev.com", "Demo123!secret"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getId()).isEqualTo(7L);
        assertThat(response.getBody().getUsername()).isEqualTo("demo");
        assertThat(response.getBody().getEmail()).isEqualTo("demo@elatus-dev.com");
        assertThat(response.getBody().getRoles())
                .containsExactly(CurrentUserDTO.RolesEnum.ADMIN, CurrentUserDTO.RolesEnum.CURATOR);
        assertThat(response.getBody().getCreatedAt())
                .isEqualTo(OffsetDateTime.of(2026, 8, 9, 14, 22, 31, 0, ZoneOffset.UTC));
        verify(registerUser, times(1)).register("demo", "demo@elatus-dev.com", "Demo123!secret");
        verifyNoMoreInteractions(registerUser);
    }

    // I10 — the response body of the one endpoint that is handed a password
    @Test
    void should_return_no_credential_material_when_a_user_registers() {
        when(registerUser.register("demo", "demo@elatus-dev.com", "Demo123!secret")).thenReturn(DEMO);

        ResponseEntity<CurrentUserDTO> response =
                controller().register(new RegisterRequestDTO("demo", "demo@elatus-dev.com", "Demo123!secret"));

        assertThat(response.getBody().toString())
                .doesNotContain("$2a$12$stored")
                .doesNotContain("Demo123!secret");
    }

    @Test
    void should_return_200_and_a_bearer_pair_when_credentials_are_accepted() {
        when(authenticateUser.authenticate("demo", "Demo123!")).thenReturn(new TokenPair("a.jwt", "r.jwt", 900L));

        ResponseEntity<TokenPairDTO> response = controller().login(new LoginRequestDTO("demo", "Demo123!"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getAccessToken()).isEqualTo("a.jwt");
        assertThat(response.getBody().getRefreshToken()).isEqualTo("r.jwt");
        assertThat(response.getBody().getTokenType()).isEqualTo(TokenPairDTO.TokenTypeEnum.BEARER);
        assertThat(response.getBody().getExpiresIn()).isEqualTo(900);
        verify(authenticateUser, times(1)).authenticate("demo", "Demo123!");
        verifyNoMoreInteractions(authenticateUser);
    }

    @Test
    void should_return_200_and_a_rotated_pair_when_a_refresh_token_is_presented() {
        when(rotateRefreshToken.rotate("r.jwt")).thenReturn(new TokenPair("a2.jwt", "r2.jwt", 900L));

        ResponseEntity<TokenPairDTO> response = controller().refreshToken(new RefreshRequestDTO("r.jwt"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getRefreshToken()).isEqualTo("r2.jwt");
        verify(rotateRefreshToken, times(1)).rotate("r.jwt");
        verifyNoMoreInteractions(rotateRefreshToken);
    }

    // logout closes the session of the token that presented it, never one named in a body
    @Test
    void should_return_204_and_close_the_presented_session_on_logout() {
        authenticateAs(ID, JTI);

        ResponseEntity<Void> response = controller().logout();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(logoutUser, times(1)).logout(JTI);
        verifyNoMoreInteractions(logoutUser);
    }

    @Test
    void should_return_the_principal_named_by_the_token_on_me() {
        authenticateAs(ID, JTI);
        when(getCurrentUser.currentUser(ID)).thenReturn(DEMO);

        ResponseEntity<CurrentUserDTO> response = controller().getCurrentUser();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getId()).isEqualTo(7L);
        verify(getCurrentUser, times(1)).currentUser(ID);
        verifyNoMoreInteractions(getCurrentUser);
    }
}
