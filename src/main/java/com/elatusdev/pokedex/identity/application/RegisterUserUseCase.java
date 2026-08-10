package com.elatusdev.pokedex.identity.application;

import com.elatusdev.pokedex.identity.domain.UserAlreadyExistsException;
import com.elatusdev.pokedex.identity.domain.Role;
import com.elatusdev.pokedex.identity.domain.User;
import com.elatusdev.pokedex.shared.domain.ClockPort;
import com.elatusdev.pokedex.identity.domain.PasswordHasher;
import com.elatusdev.pokedex.identity.domain.UserRepository;
import com.elatusdev.pokedex.identity.domain.Email;
import com.elatusdev.pokedex.identity.domain.Username;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RegisterUserUseCase {

    // ADMIN is granted, never claimed: a role taken from the request body would make
    // self-registration a privilege escalation
    private static final Set<Role> ON_REGISTRATION = Set.of(Role.CURATOR);

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final ClockPort clock;

    public RegisterUserUseCase(UserRepository users, PasswordHasher hasher, ClockPort clock) {
        this.users = users;
        this.hasher = hasher;
        this.clock = clock;
    }

    public User register(String username, String email, String rawPassword) {
        Username name = new Username(username);
        Email address = new Email(email);
        rejectTakenUsername(name);
        rejectTakenEmail(address);
        return users.save(User.register(name, address, hasher.hash(rawPassword), ON_REGISTRATION, clock.now()));
    }

    private void rejectTakenUsername(Username name) {
        if (users.existsByUsername(name)) {
            throw new UserAlreadyExistsException("username");
        }
    }

    private void rejectTakenEmail(Email address) {
        if (users.existsByEmail(address)) {
            throw new UserAlreadyExistsException("email");
        }
    }
}
