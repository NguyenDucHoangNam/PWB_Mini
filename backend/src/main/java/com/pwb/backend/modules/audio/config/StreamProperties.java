package com.pwb.backend.modules.audio.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@Getter
@Setter
@Slf4j
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

    private String cookieSecretPrevious;

    private String playlistSigningKey;

    private long playlistSignatureTtlSeconds = 600L;

    private long playlistCacheTtlSeconds = 30L;

    private String ipHashSalt;

    private String cdnOriginVerifySecret;

    private boolean cdnOriginVerifyEnabled = false;

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
        if (StringUtils.hasText(cookieSecretPrevious)) {
            if (cookieSecretPrevious.length() < MIN_SECRET_LENGTH) {
                throw new IllegalStateException(
                        "app.audio.stream.cookie-secret-previous must be at least " + MIN_SECRET_LENGTH
                                + " characters (got " + cookieSecretPrevious.length() + ")");
            }
            if (cookieSecretPrevious.equals(cookieSecret)) {
                throw new IllegalStateException(
                        "app.audio.stream.cookie-secret-previous must differ from app.audio.stream.cookie-secret");
            }
        }
        if (!StringUtils.hasText(playlistSigningKey)) {
            log.warn("STREAM_PLAYLIST_SIGNING_KEY_MISSING message=playlist HMAC disabled, generation will throw at runtime if invoked");
        } else if (playlistSigningKey.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "app.audio.stream.playlist-signing-key must be at least " + MIN_SECRET_LENGTH
                            + " characters (got " + playlistSigningKey.length() + ")");
        }
        if (cookieName.startsWith("__Host-") && !cookieSecure) {
            log.warn("STREAM_COOKIE_HOST_PREFIX_INSECURE cookieName={} message=Set cookie-secure=true to enforce __Host- prefix", cookieName);
        }
        if (cdnOriginVerifyEnabled && !StringUtils.hasText(cdnOriginVerifySecret)) {
            log.warn("STREAM_CDN_ORIGIN_VERIFY_ENABLED_BUT_SECRET_MISSING message=disable cdn-origin-verify-enabled or set secret");
        }
    }
}
