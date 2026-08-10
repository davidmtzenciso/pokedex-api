package com.elatusdev.pokedex.application.usecase;

import com.elatusdev.pokedex.domain.exception.InvalidTokenException;
import com.elatusdev.pokedex.domain.model.User;
import com.elatusdev.pokedex.domain.port.UserRepository;
import com.elatusdev.pokedex.domain.vo.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// A token whose subject no longer exists is not a 404: the credential is the thing that is
// wrong, and telling the caller the account is gone would confirm which ids were once real.
@Service
@Transactional(readOnly = true)
public class GetCurrentUserUseCase {

    private final UserRepository users;

    public GetCurrentUserUseCase(UserRepository users) {
        this.users = users;
    }

    public User currentUser(UserId id) {
        return users.findById(id).orElseThrow(InvalidTokenException::new);
    }
}
