package com.pwb.backend.common.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.jwt")
@Getter
@Setter
public class JwtProperties {

    private String secret = "dev-secret-key-change-me-in-production-min-32-bytes";
    private long accessTokenTtlSeconds = 900;
    private long refreshTokenTtlSeconds = 604800;
    private long shadowGraceSeconds = 10;
    private String issuer = "pwb-mini";
    private String headerName = "Authorization";
    private String headerPrefix = "Bearer ";
    private long blacklistClockSkewBufferSeconds = 30;
}