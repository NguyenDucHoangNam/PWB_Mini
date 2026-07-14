package com.pwb.backend.modules.iam.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.common.outbox.model.OutboxEvent;
import com.pwb.backend.common.outbox.publisher.OutboxPayloadCipher;
import com.pwb.backend.common.outbox.repository.OutboxEventRepository;
import com.pwb.backend.modules.iam.enums.UserStatus;
import com.pwb.backend.modules.iam.model.Role;
import com.pwb.backend.modules.iam.model.User;
import com.pwb.backend.modules.iam.repository.UserRepository;
import com.pwb.backend.modules.iam.service.AnonymizationReport;
import com.pwb.backend.modules.iam.service.AvatarUploadService;
import com.pwb.backend.modules.iam.service.LoginAttemptService;
import com.pwb.backend.modules.iam.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountAnonymizationServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SessionService sessionService;

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private AvatarUploadService avatarUploadService;

    @Mock
    private OutboxEventRepository outboxRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private OutboxPayloadCipher outboxCipher;

    private AccountAnonymizationServiceImpl anonymizationService;

    private final UUID userId = UUID.randomUUID();
    private User pendingUser;

    @BeforeEach
    void setUp() {
        anonymizationService = new AccountAnonymizationServiceImpl(
                userRepository,
                sessionService,
                loginAttemptService,
                avatarUploadService,
                outboxRepository,
                eventPublisher,
                objectMapper,
                outboxCipher
        );
        ReflectionTestUtils.setField(anonymizationService, "graceDays", 30);

        Role role = new Role(UUID.randomUUID(), "USER", "User");
        pendingUser = User.newPending("test@example.com", "hash", "Test Name", role);
        pendingUser.setStatus(UserStatus.PENDING_DELETION);
        ReflectionTestUtils.setField(pendingUser, "id", userId);
    }

    @Test
    void runOnce_empty() {
        when(userRepository.findExpiredPendingDeletion(any(), any(Pageable.class))).thenReturn(Collections.emptyList());

        AnonymizationReport report = anonymizationService.runOnce(10);

        assertNotNull(report);
        assertEquals(0, report.processedCount());
        assertEquals("EMPTY", report.status());
    }

    @Test
    void runOnce_processed() throws Exception {
        when(userRepository.findExpiredPendingDeletion(any(), any(Pageable.class))).thenReturn(List.of(pendingUser));
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(pendingUser));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxCipher.encrypt(anyString())).thenReturn("encrypted");

        AnonymizationReport report = anonymizationService.runOnce(10);

        assertNotNull(report);
        assertEquals(1, report.processedCount());
        assertEquals("COMPLETED", report.status());
        verify(sessionService).purgeUserSessionData(userId);
        verify(loginAttemptService).clearFailures(userId);
        verify(avatarUploadService).deleteAvatar(userId);
        verify(userRepository).save(pendingUser);
        verify(outboxRepository).save(any(OutboxEvent.class));
    }

    @Test
    void anonymizeSingle_skipIfNotPendingDeletion() {
        pendingUser.setStatus(UserStatus.ACTIVE);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(pendingUser));

        anonymizationService.anonymizeSingle(userId);

        verify(sessionService, never()).purgeUserSessionData(any());
        verify(userRepository, never()).save(any());
    }
}
