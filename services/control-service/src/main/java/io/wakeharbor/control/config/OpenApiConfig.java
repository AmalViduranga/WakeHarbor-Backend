package io.wakeharbor.control.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("WakeHarbor Control Service API")
                        .version("1.0.0")
                        .description("REST API for managing Supabase Free Plan heartbeat monitoring, schedules, executions, and alerts.")
                        .contact(new Contact().name("WakeHarbor Engineering"))
                        .license(new License().name("Proprietary")))
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth").addList("CookieAuth"))
                .components(new Components()
                        .addSecuritySchemes("BearerAuth", new SecurityScheme()
                                .name("BearerAuth")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT session token in Authorization header"))
                        .addSecuritySchemes("CookieAuth", new SecurityScheme()
                                .name("wakeharbor_session")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .description("HttpOnly session cookie")));
    }
}
