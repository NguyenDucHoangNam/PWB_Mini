package com.pwb.liveroom.application.support;

import com.pwb.liveroom.infrastructure.config.properties.LiveroomConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TurnCredentialFactory {

    private static final String HMAC_ALGORITHM = "HmacSHA1";

    private final LiveroomConfig config;

    @PostConstruct
    void validate() {
        LiveroomConfig.Turn turn = config.getRtc().getTurn();
        if (!turn.isEnabled()) {
            return;
        }
        if (urls().isEmpty()) {
            throw new IllegalStateException(
                    "pwb.liveroom.rtc.turn.enabled is true but pwb.liveroom.rtc.turn.urls is empty");
        }
        if (!StringUtils.hasText(turn.getSecret())) {
            throw new IllegalStateException(
                    "pwb.liveroom.rtc.turn.enabled is true but pwb.liveroom.rtc.turn.secret is blank");
        }
    }

    public Optional<TurnCredential> create(UUID actorId) {
        LiveroomConfig.Turn turn = config.getRtc().getTurn();
        if (!turn.isEnabled()) {
            return Optional.empty();
        }

        long expiresAt = Instant.now().plus(turn.getCredentialTtl()).getEpochSecond();
        String username = expiresAt + ":" + actorId;

        return Optional.of(new TurnCredential(urls(), username, sign(username, turn.getSecret())));
    }

    private List<String> urls() {
        return config.getRtc().getTurn().getUrls().stream()
                .map(String::trim)
                .filter(url -> !url.isEmpty())
                .toList();
    }

    private String sign(String username, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getEncoder().encodeToString(mac.doFinal(username.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to sign TURN credential", e);
        }
    }

    public record TurnCredential(List<String> urls, String username, String credential) {
    }
}
