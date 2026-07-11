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
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

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
                .setAudience(Collections.singletonList(safeClientId()))
                .build();
    }

    @Override
    public GoogleUserInfo verify(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new BusinessException(IamErrorCode.INVALID_OAUTH_TOKEN);
        }
        try {
            GoogleIdToken token = verifier.verify(idToken);
            if (token == null) {
                log.error("GOOGLE_OAUTH_FAILED error=invalid_token");
                throw new BusinessException(IamErrorCode.INVALID_OAUTH_TOKEN);
            }
            GoogleIdToken.Payload payload = token.getPayload();
            String issuer = payload.getIssuer();
            if (!EXPECTED_ISSUER.equals(issuer) && !ALTERNATE_ISSUER.equals(issuer)) {
                log.error("GOOGLE_OAUTH_FAILED error=unexpected_iss");
                throw new BusinessException(IamErrorCode.INVALID_OAUTH_TOKEN);
            }
            Boolean emailVerified = payload.getEmailVerified();
            if (Boolean.FALSE.equals(emailVerified)) {
                throw new BusinessException(IamErrorCode.INVALID_OAUTH_TOKEN);
            }
            String email = payload.getEmail();
            String sub = payload.getSubject();
            if (email == null || email.isBlank() || sub == null || sub.isBlank()) {
                throw new BusinessException(IamErrorCode.INVALID_OAUTH_TOKEN);
            }
            String name = (String) payload.get("name");
            String picture = (String) payload.get("picture");
            return new GoogleUserInfo(
                    email.trim().toLowerCase(),
                    name == null ? "" : name,
                    picture == null ? null : picture,
                    sub);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("GOOGLE_OAUTH_FAILED error={}", ex.getMessage());
            throw new BusinessException(IamErrorCode.INVALID_OAUTH_TOKEN);
        }
    }

    private String safeClientId() {
        String id = properties.getClientId();
        if (id == null || id.isBlank()) {
            log.warn("Google OAuth client id is empty. Google login will fail.");
            return "";
        }
        return id;
    }

    @PreDestroy
    void shutdown() throws Exception {
        if (httpTransport != null && httpTransport instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }

    List<String> supportedAudiences() {
        return Collections.singletonList(safeClientId());
    }
}
