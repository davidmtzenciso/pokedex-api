package com.elatusdev.pokedex.support;

import com.elatusdev.pokedex.domain.model.User;
import com.elatusdev.pokedex.domain.port.UserRepository;
import com.elatusdev.pokedex.domain.vo.Email;
import com.elatusdev.pokedex.domain.vo.UserId;
import com.elatusdev.pokedex.domain.vo.Username;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

// TEST SCOPE ONLY, and temporary. WU-US03-A owns JpaUserRepositoryAdapter and the users
// table; that work is happening on another branch in parallel, so this branch has a port
// with no adapter and no Spring context would start without one.
//
// DELETE THIS CLASS when WU-US03-A merges. Two beans of the same type is a loud startup
// failure at the merge point, which is exactly the reminder we want — it is not annotated
// @Primary precisely so that it cannot silently win over the real adapter.
@Component
public class InMemoryUserStore implements UserRepository {

    private final Map<Long, User> byId = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public Optional<User> findById(UserId id) {
        return Optional.ofNullable(byId.get(id.value()));
    }

    @Override
    public Optional<User> findByUsername(Username username) {
        return all().stream().filter(user -> user.username().equals(username)).findFirst();
    }

    @Override
    public boolean existsByUsername(Username username) {
        return findByUsername(username).isPresent();
    }

    @Override
    public boolean existsByEmail(Email email) {
        return all().stream().anyMatch(user -> user.email().equals(email));
    }

    @Override
    public User save(User user) {
        UserId id = user.id().orElseGet(() -> UserId.of(sequence.incrementAndGet()));
        User stored = User.rehydrate(
                id, user.username(), user.email(), user.passwordHash(), user.roles(), user.createdAt());
        byId.put(id.value(), stored);
        return stored;
    }

    private List<User> all() {
        return List.copyOf(byId.values());
    }
}
