package com.pwb.backend.security.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    private final Jwt jwt = new Jwt();
    private List<String> publicPaths = new ArrayList<>();

    @Getter
    @Setter
    public static class Jwt {

        private String secret;

        private String refreshSecret;

        private long accessTokenExpiration = 900_000;

        private long refreshTokenExpiration = 604_800_000;
    }
}
