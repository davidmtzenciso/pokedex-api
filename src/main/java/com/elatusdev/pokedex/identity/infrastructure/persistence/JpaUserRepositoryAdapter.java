package com.elatusdev.pokedex.identity.infrastructure.persistence;

import com.elatusdev.pokedex.identity.domain.model.User;
import com.elatusdev.pokedex.identity.domain.port.UserRepository;
import com.elatusdev.pokedex.identity.domain.vo.Email;
import com.elatusdev.pokedex.identity.domain.vo.UserId;
import com.elatusdev.pokedex.identity.domain.vo.Username;
import com.elatusdev.pokedex.identity.infrastructure.persistence.mapper.UserPersistenceMapper;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class JpaUserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpaRepository;
    private final UserPersistenceMapper mapper;

    JpaUserRepositoryAdapter(UserJpaRepository jpaRepository, UserPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<User> findById(UserId id) {
        return jpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByUsername(Username username) {
        return jpaRepository.findByUsername(username.value()).map(mapper::toDomain);
    }

    @Override
    public boolean existsByUsername(Username username) {
        return jpaRepository.existsByUsername(username.value());
    }

    @Override
    public boolean existsByEmail(Email email) {
        return jpaRepository.existsByEmail(email.value());
    }

    @Override
    @Transactional
    public User save(User user) {
        return mapper.toDomain(jpaRepository.saveAndFlush(mapper.toDataModel(user)));
    }
}
