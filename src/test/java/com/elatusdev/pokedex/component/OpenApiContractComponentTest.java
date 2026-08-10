// Copyright (c) 2026 ElatusDev
package com.elatusdev.pokedex.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiContractComponentTest {

    private static final Path AUTHORED = Path.of("src/main/resources/openapi/pokedex-api.yaml");

    private final String baseUrl;

    OpenApiContractComponentTest(@Autowired Environment environment) {
        this.baseUrl = "http://localhost:" + environment.getProperty("local.server.port") + "/api";
    }

    // AC1c — a consumer generates its types from the served document, so it must be exactly
    // what the release published, byte for byte
    @Test
    void should_serve_the_authored_contract_byte_for_byte() throws Exception {
        byte[] authored = Files.readAllBytes(AUTHORED);

        ResponseEntity<byte[]> response = fetch("/v3/api-docs.yaml");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(authored);
    }

    @Test
    void should_not_serve_a_document_rebuilt_from_annotations() {
        assertThat(fetch("/v3/api-docs").getStatusCode().value()).isEqualTo(404);
    }

    private ResponseEntity<byte[]> fetch(String path) {
        return RestClient.create()
                .get()
                .uri(baseUrl + path)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    // the status is the assertion here; the default handler would throw first
                })
                .toEntity(byte[].class);
    }
}
