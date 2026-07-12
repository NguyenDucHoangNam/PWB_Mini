package com.pwb.backend.modules.iam.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetTokenServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private PasswordResetTokenServiceImpl resetTokenService;

    private final String email = "test@example.com";

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        resetTokenService = new PasswordResetTokenServiceImpl(redisTemplate, 600L);
    }

    @Test
    void issueToken_shouldSetKeyInRedis() {
        String token = resetTokenService.issueToken(email);

        assertNotNull(token);
        verify(valueOperations).set(
                anyString(),
                eq(email),
                eq(Duration.ofSeconds(600L))
        );
    }

    @Test
    void consumeToken_shouldReturnEmailAndDeleteMapping() {
        when(valueOperations.get(anyString())).thenReturn(email);

        String token = "some-token";
        String returnedEmail = resetTokenService.consumeToken(token);

        assertEquals(email, returnedEmail);
        verify(redisTemplate).delete(anyString());
    }

    @Test
    void consumeToken_nonExistent() {
        when(valueOperations.get(anyString())).thenReturn(null);

        String returnedEmail = resetTokenService.consumeToken("bad-token");

        assertNull(returnedEmail);
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void invalidate_shouldDeleteFromRedis() {
        resetTokenService.invalidate("token-to-delete");
        verify(redisTemplate).delete(anyString());
    }
}
