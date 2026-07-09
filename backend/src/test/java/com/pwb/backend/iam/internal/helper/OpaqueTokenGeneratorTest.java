package com.pwb.backend.iam.internal.helper;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpaqueTokenGeneratorTest {

    @Test
    void generates43CharBase64UrlToken() {
        String token = OpaqueTokenGenerator.generate();
        assertEquals(43, token.length());
        assertTrue(token.matches("^[A-Za-z0-9_-]+$"));
    }

    @Test
    void tokensAreUnique() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            tokens.add(OpaqueTokenGenerator.generate());
        }
        assertEquals(1000, tokens.size());
    }

    @Test
    void twoTokensAreNeverEqual() {
        assertNotEquals(OpaqueTokenGenerator.generate(), OpaqueTokenGenerator.generate());
    }
}