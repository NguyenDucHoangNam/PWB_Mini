package com.pwb.backend.modules.iam.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pwb.backend.common.exception.GlobalExceptionHandler;
import com.pwb.backend.common.security.CurrentUserResolver;
import com.pwb.backend.modules.iam.dto.request.DeleteAccountRequest;
import com.pwb.backend.modules.iam.dto.response.UserProfileResponse;
import com.pwb.backend.modules.iam.service.AccountDeletionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AccountLifecycleControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private AccountDeletionService accountDeletionService;

    @Mock
    private CurrentUserResolver currentUserResolver;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private AccountLifecycleController accountLifecycleController;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(accountLifecycleController)
                .setControllerAdvice(new GlobalExceptionHandler(messageSource))
                .build();
        ReflectionTestUtils.setField(accountLifecycleController, "graceDays", 30);
    }

    @Test
    void deleteAccount_success() throws Exception {
        DeleteAccountRequest request = new DeleteAccountRequest("Password123", null);
        Instant now = Instant.now();
        UserProfileResponse profileResponse = new UserProfileResponse(
                userId, "test@example.com", "Test Name", "USER", "PENDING_DELETION", null, null, now
        );

        when(currentUserResolver.resolveUserId()).thenReturn(userId);
        when(accountDeletionService.requestDeletion(eq(userId), any())).thenReturn(profileResponse);
        when(messageSource.getMessage(anyString(), any(), any(), any())).thenReturn("Deletion scheduled");

        mockMvc.perform(delete("/api/v1/auth/account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING_DELETION"));
    }

    @Test
    void cancelDeletion_success() throws Exception {
        UserProfileResponse profileResponse = new UserProfileResponse(
                userId, "test@example.com", "Test Name", "USER", "ACTIVE", null, null, null
        );

        when(currentUserResolver.resolveUserId()).thenReturn(userId);
        when(accountDeletionService.cancelDeletion(userId)).thenReturn(profileResponse);
        when(messageSource.getMessage(anyString(), any(), any(), any())).thenReturn("Deletion cancelled");

        mockMvc.perform(post("/api/v1/auth/account/cancel-deletion"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void deleteAccount_responseContainsDeletionInfo() throws Exception {
        DeleteAccountRequest request = new DeleteAccountRequest("Password123", null);
        Instant now = Instant.now();
        UserProfileResponse profileResponse = new UserProfileResponse(
                userId, "test@example.com", "Test Name", "USER", "PENDING_DELETION", null, null, now
        );

        when(currentUserResolver.resolveUserId()).thenReturn(userId);
        when(accountDeletionService.requestDeletion(eq(userId), any())).thenReturn(profileResponse);
        when(messageSource.getMessage(anyString(), any(), any(), any())).thenReturn("Deletion scheduled in 30 days");

        mockMvc.perform(delete("/api/v1/auth/account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING_DELETION"))
                .andExpect(jsonPath("$.data.deletionRequestedAt").exists())
                .andExpect(jsonPath("$.data.deletionRequestedAt").isNotEmpty());
    }
}
