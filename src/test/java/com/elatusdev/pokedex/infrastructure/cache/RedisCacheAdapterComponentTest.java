package com.elatusdev.pokedex.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisCacheAdapterComponentTest {

    private GenericContainer<?> redis;
    private LettuceConnectionFactory connectionFactory;
    private RedisCacheAdapter cache;

    record CachedRow(int id, String name) {}

    @BeforeAll
    void startRedis() {
        redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
        redis.start();
        connectionFactory = connectionFactoryFor(redis);
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        cache = new RedisCacheAdapter(template);
    }

    // An unbounded command timeout does not fail open, it stalls: against a stopped Redis
    // Lettuce blocked for minutes before surfacing an error. Production bounds this through
    // spring.data.redis.timeout; the tests bound it here for the same reason.
    private static LettuceConnectionFactory connectionFactoryFor(GenericContainer<?> container) {
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(400))
                .shutdownTimeout(Duration.ZERO)
                .build();
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(container.getHost(), container.getMappedPort(6379)), clientConfig);
        factory.afterPropertiesSet();
        return factory;
    }

    @AfterAll
    void stopRedis() {
        connectionFactory.destroy();
        redis.stop();
    }

    @Test
    void should_return_what_it_stored_when_the_key_is_present() {
        cache.put("pokeapi:pokemon:1", new CachedRow(1, "bulbasaur"), Duration.ofMinutes(5));

        Optional<CachedRow> found = cache.get("pokeapi:pokemon:1", CachedRow.class);

        assertThat(found).contains(new CachedRow(1, "bulbasaur"));
    }

    @Test
    void should_return_empty_when_the_key_is_absent() {
        assertThat(cache.get("pokeapi:pokemon:absent", CachedRow.class)).isEmpty();
    }

    @Test
    void should_expire_the_entry_after_the_ttl_it_was_given() {
        cache.put("pokeapi:pokemon:2", new CachedRow(2, "ivysaur"), Duration.ofHours(24));

        assertThat(connectionFactory
                        .getConnection()
                        .keyCommands()
                        .ttl("pokeapi:pokemon:2".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .isBetween(86_000L, 86_400L);
    }

    @Test
    void should_forget_a_single_key_when_it_is_evicted() {
        cache.put("pokeapi:pokemon:3", new CachedRow(3, "venusaur"), Duration.ofMinutes(5));

        cache.evict("pokeapi:pokemon:3");

        assertThat(cache.get("pokeapi:pokemon:3", CachedRow.class)).isEmpty();
    }

    // a re-synced record must not stay shadowed by every page it used to appear in
    @Test
    void should_forget_every_key_under_a_prefix_and_leave_the_others() {
        cache.put("pokeapi:page:0:10", new CachedRow(1, "page-one"), Duration.ofMinutes(5));
        cache.put("pokeapi:page:10:10", new CachedRow(2, "page-two"), Duration.ofMinutes(5));
        cache.put("pokeapi:pokemon:4", new CachedRow(4, "charmander"), Duration.ofMinutes(5));

        cache.evictByPrefix("pokeapi:page:");

        assertThat(cache.get("pokeapi:page:0:10", CachedRow.class)).isEmpty();
        assertThat(cache.get("pokeapi:page:10:10", CachedRow.class)).isEmpty();
        assertThat(cache.get("pokeapi:pokemon:4", CachedRow.class)).contains(new CachedRow(4, "charmander"));
    }

    @Test
    void should_return_empty_rather_than_fail_when_a_cached_value_cannot_be_read_back() {
        new StringRedisTemplate(connectionFactory).opsForValue().set("pokeapi:pokemon:5", "{not json");

        assertThat(cache.get("pokeapi:pokemon:5", CachedRow.class)).isEmpty();
    }

    // A cache outage is not an outage. This is the deliberate opposite of the session
    // store, which fails closed — docs/handbook/error-handling.md.
    @Test
    void should_degrade_to_a_miss_rather_than_fail_when_redis_is_unreachable() {
        GenericContainer<?> doomed =
                new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
        doomed.start();
        LettuceConnectionFactory factory = connectionFactoryFor(doomed);
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        RedisCacheAdapter fragile = new RedisCacheAdapter(template);
        fragile.put("pokeapi:pokemon:6", new CachedRow(6, "charizard"), Duration.ofMinutes(5));

        doomed.stop();

        assertThat(fragile.get("pokeapi:pokemon:6", CachedRow.class)).isEmpty();
        assertThat(fragile.get("pokeapi:pokemon:absent", CachedRow.class)).isEmpty();
        fragile.put("pokeapi:pokemon:7", new CachedRow(7, "squirtle"), Duration.ofMinutes(5));
        fragile.evict("pokeapi:pokemon:7");
        fragile.evictByPrefix("pokeapi:page:");
        factory.destroy();
    }
}
