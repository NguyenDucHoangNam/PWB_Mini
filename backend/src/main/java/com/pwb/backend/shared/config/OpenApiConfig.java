package com.pwb.backend.shared.config;

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

  private static final String BEARER_SCHEME = "bearerAuth";
  private static final String REFRESH_COOKIE_SCHEME = "refreshCookie";

  @Bean
  public OpenAPI pwbMiniOpenApi() {
    return new OpenAPI()
        .info(new Info()
            .title("PWB MiNi Backend API")
            .version("1.0.0")
            .description("REST API for the PWB MiNi platform: identity, live rooms, audio recordings.")
            .contact(new Contact().name("PWB MiNi Engineering").email("dev@pwbmini.com"))
            .license(new License().name("Proprietary")))
        .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME).addList(REFRESH_COOKIE_SCHEME))
        .components(new Components()
            .addSecuritySchemes(BEARER_SCHEME,
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("JWT access token (Authorization: Bearer <token>)"))
            .addSecuritySchemes(REFRESH_COOKIE_SCHEME,
                new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.COOKIE)
                    .name("refreshToken")
                    .description("HttpOnly refresh token cookie set on login / verifyOtp")));
  }
}