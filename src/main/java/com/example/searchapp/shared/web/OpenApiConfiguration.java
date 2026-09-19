package com.example.searchapp.shared.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// The global security requirement below applies to every operation springdoc documents. Verified
// live (GET /v3/api-docs): today that's none — no controllers exist yet, and /health is served by
// Actuator's own handler mapping, which springdoc does not scan without the separate actuator
// integration module this repo doesn't depend on. So nothing is currently mismarked as secured. If
// a real MVC handler is ever registered at an allowlisted path (§8.1) — most plausibly GET / — its
// operation would need an explicit per-operation security override; revisit then.
@Configuration
public class OpenApiConfiguration {
  @Bean
  OpenAPI unifiedSearchOpenApi() {
    return new OpenAPI()
        .info(new Info().title("Unified Search API").version("1.0.0"))
        .components(
            new Components()
                .addSecuritySchemes(
                    "apiKey",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-API-Key")))
        .addSecurityItem(new SecurityRequirement().addList("apiKey"));
  }
}
