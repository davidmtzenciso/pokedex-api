package com.elatusdev.pokedex.shared.web.config;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

// Serves the authored file itself rather than a document rebuilt from annotations, so the
// served and published contracts cannot diverge (AC1c). A resource handler rather than a
// controller: a @RestController here would have to implement a generated *Api, and this
// endpoint is not in the contract it serves.
@Configuration
public class OpenApiContractConfig implements WebMvcConfigurer {

    static final String CONTRACT_PATH = "/v3/api-docs.yaml";
    private static final String CONTRACT_LOCATION = "classpath:/openapi/pokedex-api.yaml";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(CONTRACT_PATH)
                .addResourceLocations(CONTRACT_LOCATION)
                .setCacheControl(CacheControl.noCache())
                .resourceChain(false)
                .addResolver(new SingleFileResolver());
    }

    // the handler pattern names one file, so there is no path remainder to resolve against
    // the location — the location is the resource
    private static final class SingleFileResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            // null is PathResourceResolver's documented "not found"; an Optional here would
            // not be understood by the caller inside Spring
            return location.exists() && location.isReadable() ? location : null;
        }
    }
}
