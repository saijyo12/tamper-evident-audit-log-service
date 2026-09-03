package com.auditlogservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Global metadata for the generated OpenAPI 3 document and Swagger UI. */
@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI auditLogOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Tamper-Evident Audit Log Service API")
                        .description("Append-only audit events with integrity verification, retention, redaction, and export.")
                        .version("v1")
                        .contact(new Contact().name("Audit Log Service"))
                        .license(new License().name("Proprietary")))
                .servers(List.of(new Server().url("/").description("Current deployment")));
    }
}
