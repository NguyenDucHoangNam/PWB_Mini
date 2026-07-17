package com.pwb.iam.infrastructure.security.jwt;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.pwb.backend.exception.BusinessException;
import com.pwb.backend.exception.ErrorCode;
import com.pwb.iam.api.dto.GoogleIdTokenPayload;
import com.pwb.iam.infrastructure.security.config.GoogleProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Slf4j
@Component
public class GoogleTokenVerifier {

    private static final String GOOGLE_ISSUER = "accounts.google.com";
    private static final String GOOGLE_ISSUER_HTTPS = "https://accounts.google.com";

    private final GoogleProperties googleProperties;
    private final GoogleIdTokenVerifier delegate;

    public GoogleTokenVerifier(GoogleProperties googleProperties) {
        this.googleProperties = googleProperties;
        HttpTransport transport = new NetHttpTransport();
        GsonFactory jsonFactory = GsonFactory.getDefaultInstance();
        this.delegate = new GoogleIdTokenVerifier.Builder(transport, jsonFactory)
                .setAudience(Collections.singletonList(googleProperties.getClientId()))
                .setAcceptableTimeSkewSeconds(googleProperties.getClockSkewSeconds())
                .build();
    }

    public GoogleIdTokenPayload verify(String idTokenString) {
        String clientId = googleProperties.getClientId();
        if (clientId == null || clientId.isBlank()) {
            log.warn("Google OAuth is not configured (app.google.client-id is empty)");
            throw new BusinessException(ErrorCode.AUTH_GOOGLE_TOKEN_INVALID);
        }

        GoogleIdToken idToken;
        try {
            idToken = delegate.verify(idTokenString);
        } catch (GeneralSecurityException | IOException ex) {
            log.warn("Google token verification failed: {}", ex.getMessage());
            throw new BusinessException(ErrorCode.AUTH_GOOGLE_TOKEN_INVALID);
        }

        if (idToken == null) {
            log.warn("Google ID token rejected by verifier");
            throw new BusinessException(ErrorCode.AUTH_GOOGLE_TOKEN_INVALID);
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        String issuer = payload.getIssuer();
        if (issuer == null || (!GOOGLE_ISSUER.equals(issuer) && !GOOGLE_ISSUER_HTTPS.equals(issuer))) {
            log.warn("Unexpected Google token issuer: {}", issuer);
            throw new BusinessException(ErrorCode.AUTH_GOOGLE_TOKEN_INVALID);
        }

        String sub = payload.getSubject();
        String email = payload.getEmail();
        Boolean emailVerified = payload.getEmailVerified();
        String name = (String) payload.get("name");
        String picture = (String) payload.get("picture");

        if (sub == null || sub.isBlank() || email == null || email.isBlank()) {
            log.warn("Google ID token missing sub or email");
            throw new BusinessException(ErrorCode.AUTH_GOOGLE_TOKEN_INVALID);
        }

        if (emailVerified == null || !emailVerified) {
            log.warn("Google email not verified: email={}", email);
            throw new BusinessException(ErrorCode.AUTH_GOOGLE_EMAIL_NOT_VERIFIED);
        }

        return new GoogleIdTokenPayload(sub, email.trim().toLowerCase(), true, name, picture);
    }
}
