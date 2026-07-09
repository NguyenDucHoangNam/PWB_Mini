package com.pwb.backend.iam.internal.publisher;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboxPayloadCipherTest {

    @Test
    void disabledWhenKeyBlank() {
        OutboxPayloadCipher cipher = new OutboxPayloadCipher("");
        assertFalse(cipher.isEnabled());
        assertEquals("plain", cipher.encrypt("plain"));
        assertEquals("plain", cipher.decrypt("plain"));
    }

    @Test
    void disabledWhenKeyNull() {
        OutboxPayloadCipher cipher = new OutboxPayloadCipher(null);
        assertFalse(cipher.isEnabled());
    }

    @Test
    void encryptedRoundTrips() {
        OutboxPayloadCipher cipher = new OutboxPayloadCipher("test-key");
        assertTrue(cipher.isEnabled());
        String secret = "{\"email\":\"test@gmail.com\",\"otpCode\":\"123456\"}";
        String encrypted = cipher.encrypt(secret);
        assertTrue(encrypted.startsWith("enc:"));
        assertNotEquals(secret, encrypted);
        assertEquals(secret, cipher.decrypt(encrypted));
    }

    @Test
    void plaintextPassThroughOnDecrypt() {
        OutboxPayloadCipher cipher = new OutboxPayloadCipher("test-key");
        assertEquals("not-encrypted", cipher.decrypt("not-encrypted"));
    }

    @Test
    void nullInputPreserved() {
        OutboxPayloadCipher cipher = new OutboxPayloadCipher("test-key");
        assertNull(cipher.encrypt(null));
        assertNull(cipher.decrypt(null));
    }

    @Test
    void differentNonceEachEncryption() {
        OutboxPayloadCipher cipher = new OutboxPayloadCipher("test-key");
        String a = cipher.encrypt("hello");
        String b = cipher.encrypt("hello");
        assertNotEquals(a, b);
    }

    @Test
    void decryptInvalidDataThrows() {
        OutboxPayloadCipher cipher = new OutboxPayloadCipher("test-key");
        assertThrows(IllegalStateException.class, () -> cipher.decrypt("enc:not-valid-base64-@!"));
    }
}