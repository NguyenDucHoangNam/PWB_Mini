package com.pwb.iam.testsupport;

import com.pwb.iam.domain.model.GoogleUserInfo;
import com.pwb.iam.domain.service.GoogleTokenVerifierPort;
import com.pwb.shared.exception.BusinessException;

public final class StubGoogleTokenVerifier implements GoogleTokenVerifierPort {

    private GoogleUserInfo payload;
    private BusinessException failure;

    public StubGoogleTokenVerifier presetPayload(GoogleUserInfo payload) {
        this.payload = payload;
        this.failure = null;
        return this;
    }

    public StubGoogleTokenVerifier presetFailure(BusinessException failure) {
        this.failure = failure;
        this.payload = null;
        return this;
    }

    @Override
    public GoogleUserInfo verify(String idToken) {
        if (failure != null) {
            throw failure;
        }
        if (payload == null) {
            throw new IllegalStateException("StubGoogleTokenVerifier not configured");
        }
        return payload;
    }

    public static GoogleUserInfo defaultPayload() {
        return new GoogleUserInfo(
                "google-sub-123",
                "google@example.com",
                true,
                "Google User",
                "https://example.com/avatar.png"
        );
    }
}
