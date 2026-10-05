package com.judicialflow.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI judicialFlowOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("JudicialFlow Case Ingestion & Decision Support API")
                        .description("REST API for managing legal cases, judges, courtrooms, and hearing optimization schedules.")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("JudicialFlow Engineering Team")
                                .email("dev@judicialflow.internal"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}
