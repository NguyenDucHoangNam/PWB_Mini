package com.pwb.backend.modules.liveroom.service;

import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.modules.liveroom.config.WebRtcProperties;
import com.pwb.backend.modules.liveroom.dto.response.IceServerListResponse;
import com.pwb.backend.modules.liveroom.dto.response.IceServerListResponse.IceServerEntry;
import com.pwb.backend.modules.liveroom.exception.WebRtcErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IceServerProvider {

    private static final String HMAC_ALGORITHM = "HmacSHA1";

    private final WebRtcProperties properties;

    public IceServerListResponse provide(UUID userId) {
        String secret = properties.getTurnStaticSecret();
        if (secret == null || secret.isBlank()) {
            log.error("TURN_CREDENTIALS_FAILED userId={} reason=secret_missing", userId);
            throw new BusinessException(WebRtcErrorCode.TURN_SECRET_NOT_CONFIGURED);
        }
        List<IceServerEntry> entries = new ArrayList<>();
        if (properties.getStunServer() != null && !properties.getStunServer().isBlank()) {
            entries.add(IceServerEntry.builder()
                    .urls(List.of(properties.getStunServer()))
                    .build());
        }
        if (properties.getTurnServers() != null) {
            long expiry = Instant.now().getEpochSecond() + properties.getCredentialTtlSeconds();
            String username = expiry + ":" + userId;
            String password = generatePassword(secret, username);
            List<String> turnUrls = new ArrayList<>();
            for (String url : properties.getTurnServers()) {
                if (url != null && !url.isBlank()) {
                    turnUrls.add(url);
                }
            }
            if (!turnUrls.isEmpty()) {
                entries.add(IceServerEntry.builder()
                        .urls(turnUrls)
                        .username(username)
                        .credential(password)
                        .build());
            }
        }
        return IceServerListResponse.builder()
                .iceServers(entries)
                .build();
    }

    private String generatePassword(String secret, String username) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(username.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception ex) {
            throw new BusinessException(WebRtcErrorCode.TURN_CREDENTIALS_FAILED, ex);
        }
    }
}
