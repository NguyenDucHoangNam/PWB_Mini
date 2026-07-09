package com.pwb.backend.iam.internal.helper;

import java.security.SecureRandom;

/**
 * Generates opaque, high-entropy tokens used for short-lived, single-purpose
 * secrets such as password-reset links.
 *
 * <p>Unlike JWT-based reset tokens (which inherit the access-token signing
 * key's blast radius and remain valid until the JWT expires), opaque tokens:
 * <ul>
 *   <li>are validated by a lookup against Redis (which is the only place that
 *       stores them),</li>
 *   <li>are deleted on first use, preventing replay,</li>
 *   <li>use {@link SecureRandom} for ~256 bits of entropy,</li>
 *   <li>are URL-safe (base64url, no padding).</li>
 * </ul>
 */
public final class OpaqueTokenGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final java.util.Base64.Encoder URL_ENCODER =
        java.util.Base64.getUrlEncoder().withoutPadding();

    private OpaqueTokenGenerator() {
    }

    /**
     * Returns a fresh opaque token (43 chars base64url of 32 random bytes).
     */
    public static String generate() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }
}