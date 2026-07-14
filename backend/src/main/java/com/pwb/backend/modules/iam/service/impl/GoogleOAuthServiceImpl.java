package com.pwb.backend.modules.iam.service.impl;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.modules.iam.config.GoogleOAuthProperties;
import com.pwb.backend.modules.iam.exception.IamErrorCode;
import com.pwb.backend.modules.iam.service.GoogleOAuthService;
import com.pwb.backend.modules.iam.service.GoogleUserInfo;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
@Slf4j
public class GoogleOAuthServiceImpl implements GoogleOAuthService {

    private static final String EXPECTED_ISSUER = "https://accounts.google.com";
    private static final String ALTERNATE_ISSUER = "accounts.google.com";

    private final GoogleOAuthProperties properties;
    private final GoogleIdTokenVerifier verifier;
    private final HttpTransport httpTransport;

    public GoogleOAuthServiceImpl(GoogleOAuthProperties properties) {
        this.properties = properties;
        this.httpTransport = Utils.getDefaultTransport();
        this.verifier = new GoogleIdTokenVerifier.Builder(httpTransport, GsonFactory.getDefaultInstance())
                .setAudience(resolveAudiences())
                .build();
    }

    @Override
    public GoogleUserInfo verify(String idToken) {
        return verify(idToken, null);
    }

    @Override
    public GoogleUserInfo verify(String idToken, String expectedNonce) {
        if (idToken == null || idToken.isBlank()) {
            throw new BusinessException(IamErrorCode.INVALID_OAUTH_TOKEN);
        }
        try {
            GoogleIdToken token = verifier.verify(idToken);
            if (token == null) {
                log.warn("GOOGLE_OAUTH_FAILED error=invalid_token");
                throw new BusinessException(IamErrorCode.INVALID_OAUTH_TOKEN);
            }
            GoogleIdToken.Payload payload = token.getPayload();
            String issuer = payload.getIssuer();
            if (!EXPECTED_ISSUER.equals(issuer) && !ALTERNATE_ISSUER.equals(issuer)) {
                log.warn("GOOGLE_OAUTH_FAILED error=unexpected_iss");
                throw new BusinessException(IamErrorCode.INVALID_OAUTH_TOKEN);
            }
            Boolean emailVerified = payload.getEmailVerified();
            if (!Boolean.TRUE.equals(emailVerified)) {
                throw new BusinessException(IamErrorCode.INVALID_OAUTH_TOKEN);
            }
            String email = payload.getEmail();
            String sub = payload.getSubject();
            if (email == null || email.isBlank() || sub == null || sub.isBlank()) {
                throw new BusinessException(IamErrorCode.INVALID_OAUTH_TOKEN);
            }
            String nonce = (String) payload.get("nonce");
            if (expectedNonce != null && !expectedNonce.isBlank()) {
                if (!MessageDigest.isEqual(
                        Objects.requireNonNullElse(nonce, "").getBytes(StandardCharsets.UTF_8),
                        expectedNonce.getBytes(StandardCharsets.UTF_8))) {
                    log.warn("GOOGLE_OAUTH_FAILED error=nonce_mismatch sub={}", sub);
                    throw new BusinessException(IamErrorCode.INVALID_OAUTH_TOKEN);
                }
            }
            String name = (String) payload.get("name");
            String picture = (String) payload.get("picture");
            return GoogleUserInfo.sanitized(
                    email.trim().toLowerCase(),
                    name == null ? "" : name,
                    picture == null ? null : picture,
                    sub);
        } catch (BusinessException ex) {
            throw ex;
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("MessageDigest comparison failed", ex);
        } catch (Exception ex) {
            log.warn("GOOGLE_OAUTH_FAILED error={}", ex.getMessage());
            throw new BusinessException(IamErrorCode.INVALID_OAUTH_TOKEN);
        }
    }

    private List<String> resolveAudiences() {
        List<String> ids = properties.getClientIds() == null
                ? List.of()
                : properties.getClientIds().stream()
                        .filter(id -> id != null && !id.isBlank())
                        .toList();
        if (ids.isEmpty()) {
            log.warn("Google OAuth client ids are empty. Google login will fail.");
        }
        return ids;
    }

    @PreDestroy
    void shutdown() throws Exception {
        if (httpTransport != null && httpTransport instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }
}