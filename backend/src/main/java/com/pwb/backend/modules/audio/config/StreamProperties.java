package com.pwb.backend.modules.audio.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.audio.stream")
public class StreamProperties {

    private static final int MIN_SECRET_LENGTH = 32;

    private String cookieSecret;

    private long cookieTtlSeconds = 1800L;

    private String cookieName = "pwb_stream_sess";

    private boolean cookieSecure = true;

    private String cookieSameSite = "Strict";

    private int cidrMaskBitsIpv4 = 24;

    private int cidrMaskBitsIpv6 = 48;

    @PostConstruct
    void validate() {
        if (!StringUtils.hasText(cookieSecret)) {
            throw new IllegalStateException(
                    "app.audio.stream.cookie-secret must be set (min " + MIN_SECRET_LENGTH + " characters)");
        }
        if (cookieSecret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "app.audio.stream.cookie-secret must be at least " + MIN_SECRET_LENGTH
                            + " characters (got " + cookieSecret.length() + ")");
        }
    }
}
