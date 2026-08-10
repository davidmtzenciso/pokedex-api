// Copyright (c) 2026 ElatusDev
package com.elatusdev.pokedex.testsupport;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

// A plain @Configuration under the application's root package, so the component scan behind
// every @SpringBootTest finds it without the test having to opt in. That is deliberate: the
// JPA starter makes a datasource mandatory for the context to refresh, and requiring every
// component test in the repository to remember an @Import would turn one missing annotation
// into a red build for work units that never touch persistence.
//
// @TestConfiguration would be the usual choice and is wrong here — Boot's TypeExcludeFilter
// removes it from exactly the scan this needs to be found by.
//
// The container costs seconds and the component tier already requires a Docker daemon (R8).
@Configuration(proxyBeanMethods = false)
public class PostgresTestContainerConfig {

    private static final DockerImageName IMAGE = DockerImageName.parse("postgres:17-alpine");

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(IMAGE).withDatabaseName("pokedex").withUsername("pokedex");
    }
}
