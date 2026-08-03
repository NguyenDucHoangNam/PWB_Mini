package com.pwb.iam.domain.service;

public interface PasswordResetTokenService {

    String generateSignedToken();

    String extractRawToken(String signedToken);

    boolean verifySignature(String signedToken);

    String hashForStorage(String rawToken);

    /**
     * @param signedToken the full {@code raw.signature} token, not the raw part alone
     */
    String buildResetLink(String signedToken);
}