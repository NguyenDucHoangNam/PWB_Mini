package com.pwb.backend.modules.iam.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pwb.backend.common.exception.GlobalExceptionHandler;
import com.pwb.backend.common.security.CurrentUserResolver;
import com.pwb.backend.common.security.cookie.RefreshTokenCookieWriter;
import com.pwb.backend.common.security.jwt.BearerTokenExtractor;
import com.pwb.backend.modules.iam.service.AuthService;
import com.pwb.backend.modules.iam.service.SessionService;
import com.pwb.backend.modules.iam.session.SessionMetadata;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SessionControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private SessionService sessionService;

    @Mock
    private AuthService authService;

    @Mock
    private CurrentUserResolver currentUserResolver;

    @Mock
    private RefreshTokenCookieWriter cookieWriter;

    @Mock
    private BearerTokenExtractor bearerTokenExtractor;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private SessionController sessionController;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(sessionController)
                .setControllerAdvice(new GlobalExceptionHandler(messageSource))
                .build();
    }

    @Test
    void listSessions_success() throws Exception {
        String token = UUID.randomUUID().toString();
        SessionMetadata meta = new SessionMetadata(token, "127.0.0.1", "Chrome (Windows)", "Hanoi", Instant.now(), "sig");

        when(currentUserResolver.resolveUserId()).thenReturn(userId);
        when(cookieWriter.readRefreshCookie(any())).thenReturn(token);
        when(sessionService.listActiveSessions(userId, token)).thenReturn(List.of(meta));
        when(messageSource.getMessage(anyString(), any(), any(), any())).thenReturn("Sessions fetched");

        mockMvc.perform(get("/api/v1/auth/sessions"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].ipAddress").value("127.0.0.1"))
                .andExpect(jsonPath("$.data[0].isCurrent").value(true));
    }

    @Test
    void revokeSession_success() throws Exception {
        UUID tokenUuid = UUID.randomUUID();

        when(currentUserResolver.resolveUserId()).thenReturn(userId);
        when(cookieWriter.readRefreshCookie(any())).thenReturn("current_refresh_token");
        when(bearerTokenExtractor.extract(any())).thenReturn("access_token");
        when(authService.blacklistAccessTokenSignature("access_token")).thenReturn("signature");
        when(messageSource.getMessage(anyString(), any(), any(), any())).thenReturn("Session revoked");

        mockMvc.perform(delete("/api/v1/auth/sessions/" + tokenUuid)
                        .header("Authorization", "Bearer access_token"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(sessionService).revokeSingleSessionForCurrent(userId, tokenUuid.toString(), "current_refresh_token", "signature");
    }

    @Test
    void revokeOtherSessions_success() throws Exception {
        when(currentUserResolver.resolveUserId()).thenReturn(userId);
        when(cookieWriter.readRefreshCookie(any())).thenReturn("current_refresh_token");
        when(messageSource.getMessage(anyString(), any(), any(), any())).thenReturn("Other sessions revoked");

        mockMvc.perform(delete("/api/v1/auth/sessions"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(sessionService).revokeAllOtherSessions(userId, "current_refresh_token");
    }
}
