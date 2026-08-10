package com.elatusdev.pokedex.shared.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

// Serves the authored file itself rather than a document rebuilt from annotations, so the
// served and published contracts cannot diverge (AC1c).
//
// A functional route rather than a static resource handler, because the handler mechanism
// could not serve this from a jar: a resource location gets a trailing slash appended, and
// a directory ClassPathResource does not exist() inside a jar, so the location was dropped
// and the endpoint 404'd. It passed every test, because an exploded classpath resolves both
// forms. Reading the resource directly behaves identically in both.
//
// Not a @RestController either: OA1 requires those to implement a generated *Api, and this
// endpoint is deliberately not in the contract it serves.
@Configuration
public class OpenApiContractConfig {

    static final String CONTRACT_PATH = "/v3/api-docs.yaml";
    private static final String CONTRACT_RESOURCE = "openapi/pokedex-api.yaml";
    private static final MediaType YAML = MediaType.parseMediaType("application/yaml");

    @Bean
    RouterFunction<ServerResponse> openApiContractRoute() {
        return RouterFunctions.route()
                .GET(CONTRACT_PATH, request -> {
                    Resource contract = new ClassPathResource(CONTRACT_RESOURCE);
                    return contract.exists()
                            ? ServerResponse.ok()
                                    .contentType(YAML)
                                    .cacheControl(CacheControl.noCache())
                                    .body(contract)
                            : ServerResponse.notFound().build();
                })
                .build();
    }
}
