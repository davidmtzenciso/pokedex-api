package com.elatusdev.pokedex.identity.application.usecase;

import com.elatusdev.pokedex.identity.domain.port.SessionStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Deleting the session is what makes logout real: the access token keeps a perfect
// signature and a future exp, and every request carrying it now gets 401 (AC-AUTH-5).
@Service
@Transactional
public class LogoutUseCase {

    private final SessionStore sessions;

    public LogoutUseCase(SessionStore sessions) {
        this.sessions = sessions;
    }

    public void logout(String jti) {
        sessions.close(jti);
    }
}
