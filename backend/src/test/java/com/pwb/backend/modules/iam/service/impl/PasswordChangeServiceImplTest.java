package com.pwb.backend.modules.iam.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.common.model.OutboxEvent;
import com.pwb.backend.common.outbox.publisher.OutboxPayloadCipher;
import com.pwb.backend.common.outbox.repository.OutboxEventRepository;
import com.pwb.backend.common.util.PasswordHasher;
import com.pwb.backend.modules.iam.enums.OauthProvider;
import com.pwb.backend.modules.iam.enums.UserStatus;
import com.pwb.backend.modules.iam.exception.IamErrorCode;
import com.pwb.backend.modules.iam.model.Role;
import com.pwb.backend.modules.iam.model.User;
import com.pwb.backend.modules.iam.repository.UserRepository;
import com.pwb.backend.modules.iam.service.LoginAttemptService;
import com.pwb.backend.modules.iam.service.PasswordResetTokenService;
import com.pwb.backend.modules.iam.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordChangeServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OutboxEventRepository outboxRepository;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private PasswordResetTokenService resetTokenService;

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private SessionService sessionService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private OutboxPayloadCipher outboxCipher;

    private PasswordChangeServiceImpl passwordChangeService;

    private final UUID userId = UUID.randomUUID();
    private final String email = "test@example.com";
    private Role userRole;
    private User activeUser;

    @BeforeEach
    void setUp() {
        passwordChangeService = new PasswordChangeServiceImpl(
                userRepository,
                outboxRepository,
                passwordHasher,
                resetTokenService,
                loginAttemptService,
                sessionService,
                eventPublisher,
                objectMapper,
                outboxCipher
        );

        userRole = new Role(UUID.randomUUID(), "USER", "User");
        activeUser = User.newPending(email, "hashed_password", "Test Name", userRole);
        activeUser.setStatus(UserStatus.ACTIVE);
        ReflectionTestUtils.setField(activeUser, "id", userId);
    }

    @Test
    void requestPasswordReset_success() throws Exception {
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(activeUser));
        when(resetTokenService.issueToken(email)).thenReturn("reset-token");
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxCipher.encrypt(anyString())).thenReturn("encrypted-payload");

        passwordChangeService.requestPasswordReset(email);

        verify(resetTokenService).issueToken(email);
        verify(outboxRepository).save(any(OutboxEvent.class));
    }

    @Test
    void requestPasswordReset_noop_userNotFound() {
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(passwordHasher.hash(anyString())).thenReturn("dummy_hash");

        passwordChangeService.requestPasswordReset(email);

        verify(resetTokenService, never()).issueToken(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void resetPassword_success() {
        String token = "reset-token";
        when(resetTokenService.consumeToken(token)).thenReturn(email);
        when(userRepository.findByEmailForUpdate(email)).thenReturn(Optional.of(activeUser));
        when(passwordHasher.hash("NewPassword123")).thenReturn("new_hashed_password");

        passwordChangeService.resetPassword(token, "NewPassword123");

        assertEquals("new_hashed_password", activeUser.getPasswordHash());
        verify(userRepository).save(activeUser);
        verify(resetTokenService).invalidate(token);
        verify(loginAttemptService).clearFailures(userId);
        verify(sessionService).revokeAllSessionsCompletely(userId);
    }

    @Test
    void resetPassword_invalidToken() {
        when(resetTokenService.consumeToken("bad-token")).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                passwordChangeService.resetPassword("bad-token", "NewPassword123")
        );
        assertEquals(IamErrorCode.INVALID_RESET_TOKEN, exception.getErrorCode());
    }

    @Test
    void changePassword_success() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));
        when(passwordHasher.matches("OldPassword123", "hashed_password")).thenReturn(true);
        when(passwordHasher.matches("NewPassword123", "hashed_password")).thenReturn(false);
        when(passwordHasher.hash("NewPassword123")).thenReturn("new_hashed_password");
        when(sessionService.revokeAllSessionsExcept(userId, "current-refresh")).thenReturn(2);

        int revoked = passwordChangeService.changePassword(userId, "OldPassword123", "NewPassword123", "current-refresh");

        assertEquals(2, revoked);
        assertEquals("new_hashed_password", activeUser.getPasswordHash());
        verify(userRepository).save(activeUser);
        verify(loginAttemptService).clearFailures(userId);
    }

    @Test
    void changePassword_invalidOldPassword() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));
        when(passwordHasher.matches("BadOldPassword", "hashed_password")).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                passwordChangeService.changePassword(userId, "BadOldPassword", "NewPassword123", "current-refresh")
        );
        assertEquals(IamErrorCode.INVALID_OLD_PASSWORD, exception.getErrorCode());
        verify(loginAttemptService).recordFailure(userId);
    }

    @Test
    void changePassword_passwordReuseBlocked() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));
        when(passwordHasher.matches("SamePassword123", "hashed_password")).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                passwordChangeService.changePassword(userId, "SamePassword123", "SamePassword123", "current-refresh")
        );
        assertEquals(IamErrorCode.PASSWORD_REUSE_BLOCKED, exception.getErrorCode());
    }

    @Test
    void requestPasswordReset_oauthOnlyAccount() {
        activeUser.setOauthProvider(OauthProvider.GOOGLE);
        activeUser.setPasswordHash(null);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(activeUser));
        when(passwordHasher.hash(anyString())).thenReturn("dummy_hash");

        passwordChangeService.requestPasswordReset(email);

        verify(resetTokenService, never()).issueToken(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void requestPasswordReset_inactiveUser() {
        activeUser.setStatus(UserStatus.PENDING_DELETION);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(activeUser));
        when(passwordHasher.hash(anyString())).thenReturn("dummy_hash");

        passwordChangeService.requestPasswordReset(email);

        verify(resetTokenService, never()).issueToken(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void resetPassword_userNotFound_afterTokenConsumed() {
        String token = "reset-token";
        when(resetTokenService.consumeToken(token)).thenReturn(email);
        when(userRepository.findByEmailForUpdate(email)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () ->
                passwordChangeService.resetPassword(token, "NewPassword123")
        );
        assertEquals(IamErrorCode.INVALID_RESET_TOKEN, exception.getErrorCode());
    }

    @Test
    void changePassword_userNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () ->
                passwordChangeService.changePassword(userId, "OldPassword123", "NewPassword123", "current-refresh")
        );
        assertEquals(IamErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }
}
