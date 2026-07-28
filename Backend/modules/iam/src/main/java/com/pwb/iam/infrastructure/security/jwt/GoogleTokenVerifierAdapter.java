package com.pwb.iam.infrastructure.security.jwt;

import com.pwb.iam.api.dto.GoogleIdTokenPayload;
import com.pwb.iam.domain.service.GoogleTokenVerifierPort;
import org.springframework.stereotype.Component;

@Component
public class GoogleTokenVerifierAdapter implements GoogleTokenVerifierPort {

    private final GoogleTokenVerifier googleTokenVerifier;

    public GoogleTokenVerifierAdapter(GoogleTokenVerifier googleTokenVerifier) {
        this.googleTokenVerifier = googleTokenVerifier;
    }

    @Override
    public GoogleIdTokenPayload verify(String idToken) {
        return googleTokenVerifier.verify(idToken);
    }
}
