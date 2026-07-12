package com.pwb.backend.modules.audio.security;

import com.pwb.backend.modules.audio.config.StreamProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Slf4j
@Component
@RequiredArgsConstructor
public class IpHashUtil {

    private static final String SHA_256 = "SHA-256";
    private static final String FALLBACK_HASH = "anonymous";
    private static final int HASH_TRUNCATE_LENGTH = 16;

    private final StreamProperties streamProperties;

    public String hash(String ip) {
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            return FALLBACK_HASH;
        }
        String salt = streamProperties.getIpHashSalt();
        if (salt == null || salt.isBlank()) {
            return FALLBACK_HASH;
        }
        String payload = salt + ":" + ip;
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, HASH_TRUNCATE_LENGTH);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", ex);
        }
    }
}