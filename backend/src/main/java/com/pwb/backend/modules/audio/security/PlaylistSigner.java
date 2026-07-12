package com.pwb.backend.modules.audio.security;

import com.pwb.backend.modules.audio.config.StreamProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PlaylistSigner {

    private static final String ISSUER = "pwb-playlist";

    private final StreamProperties properties;

    private SecretKey signingKey;

    private SecretKey signingKey() {
        if (signingKey == null) {
            byte[] secretBytes = properties.getPlaylistSigningKey().getBytes(StandardCharsets.UTF_8);
            signingKey = Keys.hmacShaKeyFor(secretBytes);
        }
        return signingKey;
    }

    public SignedPlaylist sign(UUID shareToken, UUID demoId) {
        long expEpoch = Instant.now().plusSeconds(properties.getPlaylistSignatureTtlSeconds()).getEpochSecond();
        String nonce = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .issuer(ISSUER)
                .claim("shareToken", shareToken.toString())
                .claim("demoId", demoId.toString())
                .claim("exp", expEpoch)
                .claim("nonce", nonce)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.ofEpochSecond(expEpoch)))
                .signWith(signingKey(), Jwts.SIG.HS256)
                .compact();
        String sig = base64Url(token.getBytes(StandardCharsets.UTF_8));
        return new SignedPlaylist(sig, expEpoch, nonce);
    }

    public VerifyResult verify(UUID shareToken, UUID demoId, String sig, long exp, String nonce) {
        if (sig == null || sig.isBlank() || nonce == null || nonce.isBlank()) {
            return VerifyResult.missingSignature();
        }
        if (Instant.now().getEpochSecond() >= exp) {
            return VerifyResult.expired();
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(sig);
            String token = new String(decoded, StandardCharsets.UTF_8);
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .requireIssuer(ISSUER)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!shareToken.toString().equals(claims.get("shareToken", String.class))
                    || !demoId.toString().equals(claims.get("demoId", String.class))
                    || !nonce.equals(claims.get("nonce", String.class))) {
                return VerifyResult.invalid();
            }
            return VerifyResult.ok();
        } catch (Exception ex) {
            return VerifyResult.invalid();
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record SignedPlaylist(String signature, long expiresAtEpoch, String nonce) {}

    public sealed interface VerifyResult {

        static VerifyResult ok() {
            return new Ok();
        }

        static VerifyResult missingSignature() {
            return new MissingSignature();
        }

        static VerifyResult expired() {
            return new Expired();
        }

        static VerifyResult invalid() {
            return new Invalid();
        }

        record Ok() implements VerifyResult {}

        record MissingSignature() implements VerifyResult {}

        record Expired() implements VerifyResult {}

        record Invalid() implements VerifyResult {}
    }
}
