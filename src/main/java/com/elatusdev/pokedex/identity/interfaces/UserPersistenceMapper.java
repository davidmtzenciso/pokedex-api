package com.elatusdev.pokedex.identity.interfaces;

import com.elatusdev.pokedex.identity.domain.Role;
import com.elatusdev.pokedex.identity.domain.User;
import com.elatusdev.pokedex.identity.domain.Email;
import com.elatusdev.pokedex.identity.domain.PasswordHash;
import com.elatusdev.pokedex.identity.domain.UserId;
import com.elatusdev.pokedex.identity.domain.Username;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class UserPersistenceMapper {

    private static final String ROLE_SEPARATOR = ",";

    public User toDomain(UserDataModel model) {
        return User.rehydrate(
                UserId.of(model.getId()),
                new Username(model.getUsername()),
                new Email(model.getEmail()),
                new PasswordHash(model.getPasswordHash()),
                rolesOf(model.getRoles()),
                model.getCreatedAt());
    }

    public UserDataModel toDataModel(User user) {
        return new UserDataModel(
                user.id().map(UserId::value).orElse(null),
                user.username().value(),
                user.email().value(),
                user.passwordHash().value(),
                rolesText(user.roles()),
                user.createdAt());
    }

    // sorted, so the column is a function of the set: two equal role sets cannot produce two
    // different rows, and a diff of the table stays readable
    private String rolesText(Set<Role> roles) {
        return roles.stream().map(Role::name).sorted().collect(Collectors.joining(ROLE_SEPARATOR));
    }

    private Set<Role> rolesOf(String text) {
        return Arrays.stream(text.split(ROLE_SEPARATOR))
                .map(String::strip)
                .filter(name -> !name.isEmpty())
                .map(Role::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }
}
