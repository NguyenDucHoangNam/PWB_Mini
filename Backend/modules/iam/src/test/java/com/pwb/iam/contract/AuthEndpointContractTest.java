package com.pwb.iam.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.iam.api.controller.AuthController;
import com.pwb.iam.api.exception.IamExceptionHandler;
import com.pwb.iam.application.facade.AuthView;
import com.pwb.iam.application.facade.IamFacade;
import com.pwb.iam.application.usecase.ForgotPasswordUseCase;
import com.pwb.iam.domain.model.AuthNextStep;
import com.pwb.iam.testsupport.IamMessageSourceTestConfig;
import com.pwb.web.exception.GlobalExceptionHandler;
import com.pwb.web.message.MessageResolver;
import com.pwb.web.security.CurrentClientIpArgumentResolver;
import com.pwb.web.security.CurrentUserAgentArgumentResolver;
import com.pwb.web.security.CurrentUserArgumentResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@WebMvcTest(controllers = AuthController.class)
@Import({
        IamExceptionHandler.class,
        GlobalExceptionHandler.class,
        IamMessageSourceTestConfig.class,
        CurrentClientIpArgumentResolver.class,
        CurrentUserAgentArgumentResolver.class,
        CurrentUserArgumentResolver.class,
        MessageResolver.class
})
class AuthEndpointContractTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private IamFacade iamFacade;

    private final UUID testUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(iamFacade.register(any())).thenReturn(testUserId);
        when(iamFacade.verifyOtp(any())).thenReturn(authView());
        when(iamFacade.login(any())).thenReturn(authView());
        when(iamFacade.refresh(any())).thenReturn(authView());
        when(iamFacade.loginWithGoogle(any())).thenReturn(authView());
        when(iamFacade.logout(any())).thenReturn(testUserId);
        when(iamFacade.forgotPassword(any())).thenReturn(ForgotPasswordUseCase.Result.sent(testUserId, 60));
        when(iamFacade.resetPassword(any())).thenReturn(testUserId);
        when(iamFacade.changePassword(any())).thenReturn(testUserId);
    }

    private AuthView authView() {
        return AuthView.withTokens(testUserId, "user@example.com", "Alice", null, "ACTIVE", "USER",
                "access.jwt", "refresh.jwt", 900L, AuthNextStep.NONE);
    }

    @Test
    @DisplayName("POST /register response envelope must include required fields")
    void register_contract_envelope() throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "email", "user@example.com",
                "password", "StrongPass1!@#$",
                "fullName", "Alice"));

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.has("success")).isTrue();
        assertThat(json.has("data")).isTrue();
        assertThat(json.has("traceId")).isTrue();
        assertThat(json.has("timestamp")).isTrue();
        assertThat(json.get("success").asBoolean()).isTrue();
        assertThat(json.get("data").has("userId")).isTrue();
        assertThat(json.get("data").has("message")).isTrue();
        assertThat(json.get("data").get("userId").asText()).isEqualTo(testUserId.toString());
    }

    @Test
    @DisplayName("POST /verify-otp response must expose auth tokens")
    void verify_otp_contract_envelope() throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "userId", testUserId.toString(),
                "code", "123456"));

        MvcResult result = mockMvc.perform(post("/api/v1/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("data").has("accessToken")).isTrue();
        assertThat(json.get("data").has("refreshToken")).isTrue();
        assertThat(json.get("data").has("tokenType")).isTrue();
        assertThat(json.get("data").get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(json.get("data").has("expiresIn")).isTrue();
        assertThat(json.get("data").has("userId")).isTrue();
        assertThat(json.get("data").has("email")).isTrue();
        assertThat(json.get("data").has("status")).isTrue();
        assertThat(json.get("data").has("role")).isTrue();
    }

    @Test
    @DisplayName("POST /login response must expose auth tokens")
    void login_contract_envelope() throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "email", "user@example.com",
                "password", "StrongPass1!@#$"));

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("User-Agent", "TestAgent"))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("data").has("accessToken")).isTrue();
        assertThat(json.get("data").has("refreshToken")).isTrue();
        assertThat(json.get("data").has("expiresIn")).isTrue();
    }

    @Test
    @DisplayName("POST /refresh response must expose auth tokens")
    void refresh_contract_envelope() throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("refreshToken", "refresh.jwt"));

        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("data").has("accessToken")).isTrue();
        assertThat(json.get("data").has("refreshToken")).isTrue();
        assertThat(json.get("data").has("expiresIn")).isTrue();
    }

    @Test
    @DisplayName("POST /google-login response must expose auth tokens")
    void google_login_contract_envelope() throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("idToken", "valid-google-id-token-string"));

        MvcResult result = mockMvc.perform(post("/api/v1/auth/google-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Accept-Language", "vi"))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("data").has("accessToken")).isTrue();
        assertThat(json.get("data").has("refreshToken")).isTrue();
        assertThat(json.get("data").has("tokenType")).isTrue();
        assertThat(json.get("data").get("tokenType").asText()).isEqualTo("Bearer");
    }

    @Test
    @DisplayName("POST /logout response must include userId and message")
    void logout_contract_envelope() throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "refreshToken", "refresh.jwt",
                "accessJti", "jti-1",
                "accessExpiresInSeconds", 900));

        authenticateAsUser();
        MvcResult result = mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("data").has("userId")).isTrue();
        assertThat(json.get("data").get("userId").asText()).isEqualTo(testUserId.toString());
        assertThat(json.get("data").has("message")).isTrue();
    }

    @Test
    @DisplayName("POST /forgot-password response must include userId and message")
    void forgot_password_contract_envelope() throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("email", "user@example.com"));

        MvcResult result = mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("User-Agent", "TestAgent"))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("data").has("userId")).isTrue();
        assertThat(json.get("data").has("message")).isTrue();
    }

    @Test
    @DisplayName("POST /reset-password response must include userId and message")
    void reset_password_contract_envelope() throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "token", "valid-token",
                "newPassword", "NewPass1!@#$"));

        MvcResult result = mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("data").has("userId")).isTrue();
        assertThat(json.get("data").get("userId").asText()).isEqualTo(testUserId.toString());
        assertThat(json.get("data").has("message")).isTrue();
    }

    @Test
    @DisplayName("POST /change-password response must include userId and message")
    void change_password_contract_envelope() throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "currentPassword", "OldPass1!@#$",
                "newPassword", "NewPass1!@#$"));

        authenticateAsUser();
        MvcResult result = mockMvc.perform(post("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("data").has("userId")).isTrue();
        assertThat(json.get("data").has("message")).isTrue();
    }

    @Test
    @DisplayName("POST /resend-otp response must include userId and message")
    void resend_otp_contract_envelope() throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("userId", testUserId.toString()));

        MvcResult result = mockMvc.perform(post("/api/v1/auth/resend-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("data").has("userId")).isTrue();
        assertThat(json.get("data").has("message")).isTrue();
    }

    private void authenticateAsUser() {
        com.pwb.web.security.AuthenticatedUser principal = com.pwb.web.security.AuthenticatedUser.builder()
                .userId(testUserId.toString())
                .email("user@example.com")
                .authorities(java.util.Set.of("ROLE_USER"))
                .isOAuthUser(false)
                .build();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}