package com.elatusdev.pokedex.component;

import static org.assertj.core.api.Assertions.assertThat;

import com.elatusdev.pokedex.shared.infrastructure.OpenApiContractConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

// The first component test written, and deliberately so: a bean with two constructors and
// no @Autowired boots fine at compile time and fails here (IA7, risk R5).
@SpringBootTest
class ApplicationContextLoadsComponentTest {

    private final ApplicationContext context;

    ApplicationContextLoadsComponentTest(@Autowired ApplicationContext context) {
        this.context = context;
    }

    @Test
    void should_start_the_application_under_its_own_name() {
        assertThat(context.getApplicationName()).isEmpty();
        assertThat(context.getEnvironment().getProperty("spring.application.name"))
                .isEqualTo("pokedex-api");
    }

    @Test
    void should_register_the_contract_configuration() {
        assertThat(context.getBeansOfType(OpenApiContractConfig.class)).hasSize(1);
    }

    @Test
    void should_serve_under_the_api_context_path() {
        assertThat(context.getEnvironment().getProperty("server.servlet.context-path"))
                .isEqualTo("/api");
    }

    // AC-AUTH-4 — the default in-memory user prints "Using generated security password: …"
    // to stdout at boot, which is a credential in a log line. The exclusion that suppresses
    // it names a class that Boot 4 MOVED, and a wrong name there is silently ignored rather
    // than failing the boot: the exclusion did nothing for weeks and every start logged a
    // password. Asserting the bean is absent is the only version of this check that has
    // ever been evidence.
    @Test
    void should_register_no_default_user_details_service() {
        assertThat(context.getBeansOfType(org.springframework.security.core.userdetails.UserDetailsService.class))
                .isEmpty();
    }
}
