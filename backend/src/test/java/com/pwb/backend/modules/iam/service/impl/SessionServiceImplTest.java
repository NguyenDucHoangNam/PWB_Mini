package com.pwb.backend.modules.iam.service.impl;

import com.maxmind.geoip2.DatabaseReader;
import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.common.security.jwt.JwtProperties;
import com.pwb.backend.modules.iam.config.LoginProperties;
import com.pwb.backend.modules.iam.exception.IamErrorCode;
import com.pwb.backend.modules.iam.session.IssuedSession;
import com.pwb.backend.modules.iam.session.RotationResult;
import com.pwb.backend.modules.iam.session.RotationStatus;
import com.pwb.backend.modules.iam.session.SessionMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private LoginProperties loginProperties;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private DatabaseReader geoIpDatabaseReader;

    private SessionServiceImpl sessionService;

    private final UUID userId = UUID.randomUUID();
    private final String token = "refresh-token-uuid";

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        sessionService = new SessionServiceImpl(
                redisTemplate,
                loginProperties,
                jwtProperties,
                geoIpDatabaseReader,
                false
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void grantInitialSession_success_noKick() {
        when(jwtProperties.getRefreshTokenTtlSeconds()).thenReturn(604800L);
        when(loginProperties.getMaxConcurrentSessions()).thenReturn(3);
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(Object[].class)
        )).thenReturn("");

        IssuedSession session = sessionService.grantInitialSession(userId);
        assertNotNull(session);
        assertEquals(userId, session.userId());
        assertNotNull(session.refreshToken());
        assertNull(session.kickedRefreshToken());
    }

    @Test
    @SuppressWarnings("unchecked")
    void grantInitialSession_success_withKick() {
        when(jwtProperties.getRefreshTokenTtlSeconds()).thenReturn(604800L);
        when(loginProperties.getMaxConcurrentSessions()).thenReturn(3);
        String kickedToken = "kicked-token";
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(Object[].class)
        )).thenReturn(kickedToken);

        IssuedSession session = sessionService.grantInitialSession(userId);
        assertNotNull(session);
        assertEquals(kickedToken, session.kickedRefreshToken());
        verify(redisTemplate).delete("session:refresh_token:" + kickedToken);
        verify(redisTemplate).delete("session:metadata:" + kickedToken);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rotate_notFound() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(Object[].class)
        )).thenReturn(List.of("NOT_FOUND", "NONE", "", ""));

        RotationResult result = sessionService.rotate(token, userId);
        assertEquals(RotationStatus.NOT_FOUND, result.status());
    }

    @Test
    @SuppressWarnings("unchecked")
    void rotate_shadowHit() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(Object[].class)
        )).thenReturn(List.of("SHADOW_HIT", "RETURN_SHADOW", "shadow-token", ""));

        RotationResult result = sessionService.rotate(token, userId);
        assertEquals(RotationStatus.ROTATED, result.status());
        assertEquals("shadow-token", result.newRefreshToken());
        assertFalse(result.cookieUpdateRequired());
    }

    @Test
    @SuppressWarnings("unchecked")
    void rotate_theftDetected() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(Object[].class)
        )).thenReturn(List.of("REVOKED", "REVOKE_ALL", "", ""));

        Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
        when(zSetOperations.rangeWithScores("user:sessions:" + userId, 0, -1)).thenReturn(tuples);

        RotationResult result = sessionService.rotate(token, userId);
        assertEquals(RotationStatus.NOT_FOUND, result.status());
    }

    @Test
    void isActive_true() {
        when(redisTemplate.hasKey("session:refresh_token:" + token)).thenReturn(true);
        assertTrue(sessionService.isActive(token));
    }

    @Test
    void findUserIdForRefreshToken_success() {
        when(valueOperations.get("session:refresh_token:" + token)).thenReturn(userId.toString());
        assertEquals(userId.toString(), sessionService.findUserIdForRefreshToken(token));
    }

    @Test
    void revokeSingleSession_success() {
        when(valueOperations.get("session:refresh_token:" + token)).thenReturn(userId.toString());
        sessionService.revokeSingleSession(token);
        verify(redisTemplate).delete("session:refresh_token:" + token);
        verify(redisTemplate).delete("session:metadata:" + token);
        verify(zSetOperations).remove("user:sessions:" + userId, token);
    }

    @Test
    void revokeSingleSessionForCurrent_throwsExceptionIfCurrent() {
        BusinessException exception = assertThrows(BusinessException.class, () ->
                sessionService.revokeSingleSessionForCurrent(userId, token, token, "signature")
        );
        assertEquals(IamErrorCode.CANNOT_REVOKE_CURRENT_SESSION, exception.getErrorCode());
    }

    @Test
    void revokeSingleSessionForCurrent_throwsExceptionIfNotFound() {
        when(zSetOperations.rank("user:sessions:" + userId, token)).thenReturn(null);
        BusinessException exception = assertThrows(BusinessException.class, () ->
                sessionService.revokeSingleSessionForCurrent(userId, token, "other-token", "signature")
        );
        assertEquals(IamErrorCode.SESSION_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void revokeSingleSessionForCurrent_success() {
        when(zSetOperations.rank("user:sessions:" + userId, token)).thenReturn(1L);
        when(hashOperations.get("session:metadata:" + token, "active_jwt_signature")).thenReturn("sig-to-revoke");
        when(jwtProperties.getAccessTokenTtlSeconds()).thenReturn(900L);
        when(jwtProperties.getBlacklistClockSkewBufferSeconds()).thenReturn(30L);

        sessionService.revokeSingleSessionForCurrent(userId, token, "other-token", "current-sig");

        verify(redisTemplate).delete("session:refresh_token:" + token);
        verify(redisTemplate).delete("session:metadata:" + token);
        verify(zSetOperations).remove("user:sessions:" + userId, token);
        verify(valueOperations).set(eq("session:blacklist_token:sig-to-revoke"), eq("true"), any(Duration.class));
    }

    @Test
    void listActiveSessions_empty() {
        when(zSetOperations.rangeWithScores("user:sessions:" + userId, 0, -1)).thenReturn(Collections.emptySet());
        List<SessionMetadata> list = sessionService.listActiveSessions(userId, token);
        assertTrue(list.isEmpty());
    }

    @Test
    void writeSessionMetadata_success() {
        sessionService.writeSessionMetadata(userId, token, "access-sig", "127.0.0.1", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0", "Hanoi");
        verify(hashOperations).putAll(eq("session:metadata:" + token), anyMap());
    }

    @Test
    void isAccessTokenBlacklisted_true() {
        when(redisTemplate.hasKey("session:blacklist_token:signature")).thenReturn(true);
        assertTrue(sessionService.isAccessTokenBlacklisted("signature"));
    }

    @Test
    void isAccessTokenBlacklisted_false() {
        when(redisTemplate.hasKey("session:blacklist_token:signature")).thenReturn(false);
        assertFalse(sessionService.isAccessTokenBlacklisted("signature"));
    }

    @Test
    void blacklistAccessToken_success() {
        sessionService.blacklistAccessToken("signature", 930L);

        verify(valueOperations).set(eq("session:blacklist_token:signature"), eq("true"), any(Duration.class));
    }

    @Test
    void revokeAllSessions_success() {
        Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
        tuples.add(createTuple("token1", 1.0));
        tuples.add(createTuple("token2", 2.0));

        when(zSetOperations.rangeWithScores("user:sessions:" + userId, 0, -1)).thenReturn(tuples);
        when(jwtProperties.getRefreshTokenTtlSeconds()).thenReturn(604800L);

        sessionService.revokeAllSessions(userId);

        verify(redisTemplate).delete("session:refresh_token:token1");
        verify(redisTemplate).delete("session:refresh_token:token2");
        verify(redisTemplate).delete("session:metadata:token1");
        verify(redisTemplate).delete("session:metadata:token2");
        verify(redisTemplate).delete("user:sessions:" + userId);
    }

    @Test
    void revokeAllSessionsExcept_success() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of("sig1", "sig2"));

        int revoked = sessionService.revokeAllSessionsExcept(userId, "current-token");

        assertEquals(2, revoked);
    }

    @Test
    @SuppressWarnings("unchecked")
    void revokeAllSessionsCompletely_success() {
        Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
        tuples.add(createTuple("token1", 1.0));
        tuples.add(createTuple("token2", 2.0));

        when(zSetOperations.rangeWithScores("user:sessions:" + userId, 0, -1)).thenReturn(tuples);
        when(jwtProperties.getRefreshTokenTtlSeconds()).thenReturn(604800L);

        int revoked = sessionService.revokeAllSessionsCompletely(userId);

        assertEquals(2, revoked);
    }

    @Test
    void purgeUserSessionData_success() {
        Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
        tuples.add(createTuple("token1", 1.0));

        when(zSetOperations.rangeWithScores("user:sessions:" + userId, 0, -1)).thenReturn(tuples);
        when(jwtProperties.getRefreshTokenTtlSeconds()).thenReturn(604800L);

        sessionService.purgeUserSessionData(userId);
    }

    private ZSetOperations.TypedTuple<String> createTuple(String value, double score) {
        return new ZSetOperations.TypedTuple<String>() {
            @Override
            public String getValue() {
                return value;
            }

            @Override
            public Double getScore() {
                return score;
            }

            @Override
            public int compareTo(ZSetOperations.TypedTuple<String> o) {
                return Double.compare(score, o.getScore());
            }
        };
    }
}
