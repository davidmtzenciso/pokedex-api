package com.elatusdev.pokedex.component;

import static org.assertj.core.api.Assertions.assertThat;

import com.elatusdev.pokedex.web.config.OpenApiContractConfig;
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
}
