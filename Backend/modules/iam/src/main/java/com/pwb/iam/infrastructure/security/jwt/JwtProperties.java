package com.pwb.iam.infrastructure.security.jwt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtProperties {

    private String secret;

    private String refreshSecret;

    private long accessTokenExpiration = 900_000L;

    private long refreshTokenExpiration = 604_800_000L;
}
