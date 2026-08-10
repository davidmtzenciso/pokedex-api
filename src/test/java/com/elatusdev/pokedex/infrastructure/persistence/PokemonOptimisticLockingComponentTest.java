package com.elatusdev.pokedex.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.elatusdev.pokedex.domain.model.Pokemon;
import com.elatusdev.pokedex.domain.port.PokemonRepository;
import com.elatusdev.pokedex.domain.vo.Notes;
import com.elatusdev.pokedex.domain.vo.PokemonId;
import com.elatusdev.pokedex.domain.vo.Region;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

// Two curators read the same record and both write. Without @Version the second write wins
// silently and the first curator's edit is gone — a data-loss bug with no error, no log line
// and no way to notice for months. This is the test that says it cannot happen.
@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PokemonOptimisticLockingComponentTest {

    private final PokemonRepository repository;
    private final JdbcTemplate jdbc;

    PokemonOptimisticLockingComponentTest(@Autowired PokemonRepository repository, @Autowired JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void clearDatabase() {
        PokemonFixture.clear(jdbc);
    }

    @Test
    void should_reject_the_second_write_when_both_curators_started_from_the_same_version() {
        PokemonId id = repository.save(PokemonFixture.syncedBulbasaur()).id().orElseThrow();
        Pokemon readByFirstCurator = repository.findById(id).orElseThrow();
        Pokemon readBySecondCurator = repository.findById(id).orElseThrow();
        assertThat(readByFirstCurator.version()).isEqualTo(readBySecondCurator.version());

        readByFirstCurator.assignRegion(Region.KANTO);
        repository.save(readByFirstCurator);
        readBySecondCurator.assignRegion(Region.JOHTO);

        assertThatThrownBy(() -> repository.save(readBySecondCurator))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void should_keep_the_first_write_when_the_second_is_rejected() {
        PokemonId id = repository.save(PokemonFixture.syncedBulbasaur()).id().orElseThrow();
        Pokemon readByFirstCurator = repository.findById(id).orElseThrow();
        Pokemon readBySecondCurator = repository.findById(id).orElseThrow();

        readByFirstCurator.annotate(new Notes("written first"));
        repository.save(readByFirstCurator);
        readBySecondCurator.annotate(new Notes("lost update"));
        assertThatThrownBy(() -> repository.save(readBySecondCurator))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(repository.findById(id).orElseThrow().proprietary().notes())
                .contains(new Notes("written first"));
    }

    @Test
    void should_accept_the_second_write_when_it_starts_from_the_version_the_first_produced() {
        PokemonId id = repository.save(PokemonFixture.syncedBulbasaur()).id().orElseThrow();
        Pokemon first = repository.findById(id).orElseThrow();
        first.assignRegion(Region.KANTO);
        repository.save(first);

        Pokemon reread = repository.findById(id).orElseThrow();
        reread.annotate(new Notes("second, after a reload"));
        Pokemon second = repository.save(reread);

        assertThat(second.version()).isEqualTo(2L);
        assertThat(second.proprietary().region()).contains(Region.KANTO);
    }
}
