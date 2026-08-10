package com.elatusdev.pokedex.web.controller;

import com.elatusdev.pokedex.application.usecase.AuthenticateUserUseCase;
import com.elatusdev.pokedex.application.usecase.GetCurrentUserUseCase;
import com.elatusdev.pokedex.application.usecase.LogoutUseCase;
import com.elatusdev.pokedex.application.usecase.RefreshTokenRotationUseCase;
import com.elatusdev.pokedex.application.usecase.RegisterUserUseCase;
import com.elatusdev.pokedex.application.usecase.TokenPair;
import com.elatusdev.pokedex.domain.exception.InvalidTokenException;
import com.elatusdev.pokedex.domain.model.Role;
import com.elatusdev.pokedex.domain.model.User;
import com.elatusdev.pokedex.web.api.SecurityApi;
import com.elatusdev.pokedex.web.dto.CurrentUserDTO;
import com.elatusdev.pokedex.web.dto.LoginRequestDTO;
import com.elatusdev.pokedex.web.dto.RefreshRequestDTO;
import com.elatusdev.pokedex.web.dto.RegisterRequestDTO;
import com.elatusdev.pokedex.web.dto.TokenPairDTO;
import com.elatusdev.pokedex.web.security.Principals;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SecurityController implements SecurityApi {

    private final RegisterUserUseCase registerUser;
    private final AuthenticateUserUseCase authenticateUser;
    private final RefreshTokenRotationUseCase rotateRefreshToken;
    private final LogoutUseCase logoutUser;
    private final GetCurrentUserUseCase getCurrentUser;

    public SecurityController(
            RegisterUserUseCase registerUser,
            AuthenticateUserUseCase authenticateUser,
            RefreshTokenRotationUseCase rotateRefreshToken,
            LogoutUseCase logoutUser,
            GetCurrentUserUseCase getCurrentUser) {
        this.registerUser = registerUser;
        this.authenticateUser = authenticateUser;
        this.rotateRefreshToken = rotateRefreshToken;
        this.logoutUser = logoutUser;
        this.getCurrentUser = getCurrentUser;
    }

    @Override
    public ResponseEntity<CurrentUserDTO> register(RegisterRequestDTO body) {
        User registered = registerUser.register(body.getUsername(), body.getEmail(), body.getPassword());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(registered));
    }

    @Override
    public ResponseEntity<TokenPairDTO> login(LoginRequestDTO body) {
        return ResponseEntity.ok(toDto(authenticateUser.authenticate(body.getUsername(), body.getPassword())));
    }

    @Override
    public ResponseEntity<TokenPairDTO> refreshToken(RefreshRequestDTO body) {
        return ResponseEntity.ok(toDto(rotateRefreshToken.rotate(body.getRefreshToken())));
    }

    @Override
    public ResponseEntity<Void> logout() {
        logoutUser.logout(Principals.current().jti());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<CurrentUserDTO> getCurrentUser() {
        return ResponseEntity.ok(toDto(getCurrentUser.currentUser(Principals.current().subject())));
    }

    private static TokenPairDTO toDto(TokenPair pair) {
        return new TokenPairDTO(
                pair.accessToken(),
                pair.refreshToken(),
                TokenPairDTO.TokenTypeEnum.BEARER,
                Math.toIntExact(pair.expiresInSeconds()));
    }

    private static CurrentUserDTO toDto(User user) {
        List<CurrentUserDTO.RolesEnum> roles = user.roles().stream()
                .map(Role::name)
                .sorted()
                .map(CurrentUserDTO.RolesEnum::fromValue)
                .toList();
        CurrentUserDTO dto = new CurrentUserDTO(
                user.id().orElseThrow(InvalidTokenException::new).value(), user.username().value(), roles);
        dto.setEmail(user.email().value());
        dto.setCreatedAt(user.createdAt().atOffset(ZoneOffset.UTC));
        return dto;
    }
}
