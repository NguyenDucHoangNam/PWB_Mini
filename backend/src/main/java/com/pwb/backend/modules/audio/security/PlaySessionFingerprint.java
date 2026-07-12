package com.pwb.backend.modules.audio.security;

import com.pwb.backend.common.security.HttpClientContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
@RequiredArgsConstructor
public class PlaySessionFingerprint {

    private static final String SHA_256 = "SHA-256";
    private static final String DELIMITER = "|";

    private final HttpClientContextResolver clientContextResolver;

    public String compute(HttpServletRequest request) {
        String ip = safe(clientContextResolver.resolveIp(request));
        String userAgent = safe(request.getHeader("User-Agent"));
        String acceptLanguage = safe(request.getHeader("Accept-Language"));
        String secChUa = safe(request.getHeader("Sec-CH-UA"));
        String secChUaPlatform = safe(request.getHeader("Sec-CH-UA-Platform"));

        String payload = String.join(DELIMITER,
                ip, userAgent, acceptLanguage, secChUa, secChUaPlatform);

        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable", ex);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
