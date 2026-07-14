package com.pwb.backend.modules.iam.service.impl;

import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.common.outbox.OutboxService;
import com.pwb.backend.common.util.PasswordHasher;
import com.pwb.backend.modules.iam.dto.request.DeleteAccountRequest;
import com.pwb.backend.modules.iam.dto.response.UserProfileResponse;
import com.pwb.backend.modules.iam.enums.OauthProvider;
import com.pwb.backend.modules.iam.enums.UserStatus;
import com.pwb.backend.modules.iam.exception.IamErrorCode;
import com.pwb.backend.modules.iam.mapper.UserMapper;
import com.pwb.backend.modules.iam.model.Role;
import com.pwb.backend.modules.iam.model.User;
import com.pwb.backend.modules.iam.repository.UserRepository;
import com.pwb.backend.modules.iam.service.GoogleOAuthService;
import com.pwb.backend.modules.iam.service.GoogleUserInfo;
import com.pwb.backend.modules.iam.service.LoginAttemptService;
import com.pwb.backend.modules.iam.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private SessionService sessionService;

    @Mock
    private GoogleOAuthService googleOAuthService;

    @Mock
    private OutboxService outboxService;

    @Mock
    private UserMapper userMapper;

    private AccountDeletionServiceImpl accountDeletionService;

    private final UUID userId = UUID.randomUUID();
    private final String email = "test@example.com";
    private Role userRole;
    private User activeUser;

    @BeforeEach
    void setUp() {
        accountDeletionService = new AccountDeletionServiceImpl(
                userRepository,
                passwordHasher,
                loginAttemptService,
                sessionService,
                googleOAuthService,
                outboxService,
                userMapper
        );
        ReflectionTestUtils.setField(accountDeletionService, "graceDays", 30);

        userRole = new Role(UUID.randomUUID(), "USER", "User");
        activeUser = User.newPending(email, "hashed_password", "Test Name", userRole);
        activeUser.setStatus(UserStatus.ACTIVE);
        ReflectionTestUtils.setField(activeUser, "id", userId);
    }

    @Test
    void requestDeletion_success_local() {
        DeleteAccountRequest request = new DeleteAccountRequest("Password123", null);

        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(activeUser));
        when(passwordHasher.matches("Password123", "hashed_password")).thenReturn(true);
        when(userMapper.toUserProfileResponse(activeUser))
                .thenReturn(new UserProfileResponse(userId, email, "Test Name", "USER", "PENDING_DELETION", null, null, null));

        UserProfileResponse response = accountDeletionService.requestDeletion(userId, request);

        assertNotNull(response);
        assertEquals(UserStatus.PENDING_DELETION, activeUser.getStatus());
        verify(userRepository).save(activeUser);
        verify(sessionService).purgeUserSessionData(userId);
        verify(outboxService).publish(anyString(), any(), anyString(), anyString(), any());
    }

    @Test
    void requestDeletion_success_oauth() {
        activeUser.setOauthProvider(OauthProvider.GOOGLE);
        activeUser.setOauthId("google-sub-id");
        activeUser.setPasswordHash(null);
        DeleteAccountRequest request = new DeleteAccountRequest(null, "google_id_token");

        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(activeUser));
        GoogleUserInfo googleUserInfo = new GoogleUserInfo(email, "Test Name", "avatar_url", "google-sub-id");
        when(googleOAuthService.verify("google_id_token")).thenReturn(googleUserInfo);

        accountDeletionService.requestDeletion(userId, request);

        assertEquals(UserStatus.PENDING_DELETION, activeUser.getStatus());
    }

    @Test
    void requestDeletion_throwsReauthRequired_bothPassed() {
        DeleteAccountRequest request = new DeleteAccountRequest("Password123", "google_id_token");
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(activeUser));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                accountDeletionService.requestDeletion(userId, request)
        );
        assertEquals(IamErrorCode.REAUTH_REQUIRED, exception.getErrorCode());
    }

    @Test
    void requestDeletion_throwsBanned() {
        activeUser.setStatus(UserStatus.BANNED);
        DeleteAccountRequest request = new DeleteAccountRequest("Password123", null);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(activeUser));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                accountDeletionService.requestDeletion(userId, request)
        );
        assertEquals(IamErrorCode.ACCOUNT_BANNED, exception.getErrorCode());
    }

    @Test
    void cancelDeletion_success() {
        activeUser.setStatus(UserStatus.PENDING_DELETION);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(activeUser));
        when(userMapper.toUserProfileResponse(activeUser))
                .thenReturn(new UserProfileResponse(userId, email, "Test Name", "USER", "ACTIVE", null, null, null));

        UserProfileResponse response = accountDeletionService.cancelDeletion(userId);

        assertNotNull(response);
        assertEquals(UserStatus.ACTIVE, activeUser.getStatus());
        verify(userRepository).save(activeUser);
        verify(outboxService).publish(anyString(), any(), anyString(), anyString(), any());
    }

    @Test
    void cancelDeletion_throwsNotPendingDeletion() {
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(activeUser));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                accountDeletionService.cancelDeletion(userId)
        );
        assertEquals(IamErrorCode.USER_NOT_PENDING_DELETION, exception.getErrorCode());
    }

    @Test
    void requestDeletion_invalidPassword() {
        DeleteAccountRequest request = new DeleteAccountRequest("WrongPassword123", null);

        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(activeUser));
        when(passwordHasher.matches("WrongPassword123", "hashed_password")).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                accountDeletionService.requestDeletion(userId, request)
        );
        assertEquals(IamErrorCode.INVALID_PASSWORD_REAUTH, exception.getErrorCode());
    }

    @Test
    void requestDeletion_userNotFound() {
        DeleteAccountRequest request = new DeleteAccountRequest("Password123", null);

        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () ->
                accountDeletionService.requestDeletion(userId, request)
        );
        assertEquals(IamErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }
}