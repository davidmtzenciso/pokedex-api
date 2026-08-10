package com.elatusdev.pokedex.testsupport;

import com.elatusdev.pokedex.pokedex.domain.model.EvolutionLink;
import com.elatusdev.pokedex.pokedex.domain.model.LocalizedName;
import com.elatusdev.pokedex.pokedex.domain.model.NameSource;
import com.elatusdev.pokedex.pokedex.domain.model.Pokemon;
import com.elatusdev.pokedex.pokedex.domain.model.PokemonAbility;
import com.elatusdev.pokedex.pokedex.domain.model.PokemonStat;
import com.elatusdev.pokedex.pokedex.domain.model.PokemonType;
import com.elatusdev.pokedex.pokedex.domain.model.ReplicatedFields;
import com.elatusdev.pokedex.pokedex.domain.model.ReplicationState;
import com.elatusdev.pokedex.identity.domain.model.Role;
import com.elatusdev.pokedex.identity.domain.model.User;
import com.elatusdev.pokedex.shared.domain.vo.Category;
import com.elatusdev.pokedex.shared.domain.vo.Description;
import com.elatusdev.pokedex.identity.domain.vo.Email;
import com.elatusdev.pokedex.shared.domain.vo.Height;
import com.elatusdev.pokedex.shared.domain.vo.Mass;
import com.elatusdev.pokedex.identity.domain.vo.PasswordHash;
import com.elatusdev.pokedex.shared.domain.vo.PokeApiId;
import com.elatusdev.pokedex.shared.domain.vo.PokemonName;
import com.elatusdev.pokedex.shared.domain.vo.Sprite;
import com.elatusdev.pokedex.identity.domain.vo.Username;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PokemonFixture {

    public static final Instant SYNCED_AT = Instant.parse("2026-08-09T12:00:00Z");

    private PokemonFixture() {
    }

    // TRUNCATE rather than DELETE: it resets the identity sequences too, so a test that
    // asserts on an id is not a hostage to the order the class happened to run in.
    public static void clear(JdbcTemplate jdbc) {
        jdbc.execute("TRUNCATE TABLE pokemon, users RESTART IDENTITY CASCADE");
    }

    public static ReplicatedFields bulbasaur() {
        return new ReplicatedFields(
                new PokemonName("bulbasaur"),
                Optional.of(new Category("Seed Pokémon")),
                // IA3 — 69 hectograms is 6.9 kg, 7 decimetres is 0.7 m
                Mass.ofHectograms(69),
                Height.ofDecimetres(7),
                64,
                new Sprite(URI.create("https://img.example/1.png"), URI.create("https://img.example/1-art.png")),
                Optional.of(new Description("A strange seed was planted on its back at birth.")),
                List.of(new PokemonAbility("overgrow", 1, false), new PokemonAbility("chlorophyll", 3, true)),
                List.of(new PokemonStat("hp", 45, 0), new PokemonStat("attack", 49, 0)),
                List.of(new PokemonType("grass", 1), new PokemonType("poison", 2)),
                List.of(new EvolutionLink(PokeApiId.of(1), PokeApiId.of(2), "level-up", Optional.of(16))),
                List.of(
                        new LocalizedName("ja", "フシギダネ", NameSource.UPSTREAM),
                        new LocalizedName("fr", "Bulbizarre", NameSource.UPSTREAM)));
    }

    // every replicated component differs from bulbasaur(), so a merge that quietly did
    // nothing cannot pass a test that compares against this
    public static ReplicatedFields changedUpstream() {
        return new ReplicatedFields(
                new PokemonName("bulbasaur-redux"),
                Optional.of(new Category("Renamed Pokémon")),
                Mass.ofHectograms(70),
                Height.ofDecimetres(8),
                65,
                new Sprite(URI.create("https://img.example/1-v2.png"), URI.create("https://img.example/1-art-v2.png")),
                Optional.of(new Description("Upstream rewrote this entry.")),
                List.of(new PokemonAbility("overgrow", 1, true)),
                List.of(new PokemonStat("hp", 46, 1)),
                List.of(new PokemonType("grass", 1)),
                List.of(new EvolutionLink(PokeApiId.of(1), PokeApiId.of(3), "trade", Optional.empty())),
                List.of(new LocalizedName("de", "Bisasam", NameSource.UPSTREAM)));
    }

    public static Pokemon synced(int pokeApiId, ReplicatedFields replicated) {
        Pokemon pokemon = Pokemon.pending(PokeApiId.of(pokeApiId), replicated);
        pokemon.transitionTo(ReplicationState.SYNCED, SYNCED_AT);
        return pokemon;
    }

    public static Pokemon syncedBulbasaur() {
        return synced(1, bulbasaur());
    }

    public static Pokemon draft(String name) {
        return Pokemon.draft(new ReplicatedFields(
                new PokemonName(name),
                Optional.empty(),
                Mass.ofHectograms(10),
                Height.ofDecimetres(5),
                1,
                Sprite.NONE,
                Optional.empty(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()));
    }

    public static User curator(String username) {
        return User.register(
                new Username(username),
                new Email(username + "@example.com"),
                new PasswordHash("$2a$10$abcdefghijklmnopqrstuv"),
                Set.of(Role.CURATOR),
                Instant.parse("2026-08-01T09:00:00Z"));
    }
}
