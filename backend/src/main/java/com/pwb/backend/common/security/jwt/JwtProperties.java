package com.pwb.backend.common.security.jwt;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

@ConfigurationProperties(prefix = "app.security.jwt")
@Getter
@Setter
public class JwtProperties {

    private static final int MIN_SECRET_LENGTH = 32;
    private static final String DEFAULT_DEV_SECRET_PREFIX = "dev-secret-key";
    private static final List<String> PROD_PROFILES = List.of("prod", "production");

    private String secret;
    private String refreshSecret;
    private long accessTokenTtlSeconds;
    private long refreshTokenTtlSeconds;
    private long shadowGraceSeconds;
    private String issuer;
    private String headerName;
    private String headerPrefix;
    private long blacklistClockSkewBufferSeconds;

    private final Environment environment;

    @Autowired
    public JwtProperties(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        boolean isProd = isProdProfileActive();
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException(
                    "app.security.jwt.secret must be set (min " + MIN_SECRET_LENGTH + " characters)");
        }
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "app.security.jwt.secret must be at least " + MIN_SECRET_LENGTH + " characters (got "
                            + secret.length() + ")");
        }
        if (isProd && secret.startsWith(DEFAULT_DEV_SECRET_PREFIX)) {
            throw new IllegalStateException(
                    "app.security.jwt.secret appears to be the bundled development value; "
                            + "replace it with a strong random secret before running with profile=prod");
        }
        if (StringUtils.hasText(refreshSecret)) {
            if (refreshSecret.length() < MIN_SECRET_LENGTH) {
                throw new IllegalStateException(
                        "app.security.jwt.refresh-secret must be at least " + MIN_SECRET_LENGTH + " characters");
            }
            if (refreshSecret.equals(secret)) {
                throw new IllegalStateException(
                        "app.security.jwt.refresh-secret must differ from app.security.jwt.secret");
            }
        }
    }

    private boolean isProdProfileActive() {
        if (environment == null) {
            return false;
        }
        String[] active = environment.getActiveProfiles();
        if (active == null || active.length == 0) {
            return false;
        }
        return Arrays.stream(active).anyMatch(PROD_PROFILES::contains);
    }
}
