package com.pwb.iam.domain.service;

public interface PasswordResetTokenService {

    String generateSignedToken();

    String extractRawToken(String signedToken);

    boolean verifySignature(String signedToken);

    String hashForStorage(String rawToken);

    String buildResetLink(String rawToken);
}
