package com.pwb.backend.iam.internal.helper;

import java.security.SecureRandom;
import java.util.Base64;

public class OpaqueTokenGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER =
        Base64.getUrlEncoder().withoutPadding();

    private OpaqueTokenGenerator() {
    }

    public static String generate() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }
}