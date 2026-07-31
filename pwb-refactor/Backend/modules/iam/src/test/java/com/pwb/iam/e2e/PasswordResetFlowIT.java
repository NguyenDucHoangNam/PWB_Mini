package com.pwb.iam.e2e;

import com.pwb.iam.api.dto.request.ForgotPasswordRequest;
import com.pwb.iam.api.dto.request.LoginRequest;
import com.pwb.iam.api.dto.request.RegisterRequest;
import com.pwb.iam.api.dto.request.ResetPasswordRequest;
import com.pwb.iam.api.dto.request.VerifyOtpRequest;
import com.pwb.iam.api.dto.response.AuthMessageResponse;
import com.pwb.iam.api.dto.response.AuthResponse;
import com.pwb.iam.testsupport.AbstractE2EIT;
import com.pwb.shared.dto.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Password Reset Flow E2E — forgot/reset/login cycle")
class PasswordResetFlowIT extends AbstractE2EIT {

    private static final String STRONG_PASSWORD = "Str0ng!Pass#2026";
    private static final String NEW_PASSWORD = "N3w$ecure!Pass#99";

    private UUID registerAndVerify(String email) {
        ResponseEntity<ApiResponse<AuthMessageResponse>> regResp = restClient().post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterRequest(email, STRONG_PASSWORD, "Reset User"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {})
                .toEntity(new ParameterizedTypeReference<ApiResponse<AuthMessageResponse>>() {});

        assertThat(regResp.getStatusCode().value()).isEqualTo(201);
        UUID userId = regResp.getBody().getData().userId();

        String otp = latestOtpCode(email);
        ResponseEntity<ApiResponse<AuthResponse>> verifyResp = restClient().post()
                .uri("/api/v1/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new VerifyOtpRequest(userId, otp))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {})
                .toEntity(new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {});

        assertThat(verifyResp.getStatusCode().value()).isEqualTo(200);
        return userId;
    }

    private int loginStatus(String email, String password) {
        ResponseEntity<ApiResponse<AuthResponse>> resp = restClient().post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(email, password))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp2) -> {})
                .toEntity(new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {});
        return resp.getStatusCode().value();
    }

    @Test
    @DisplayName("🟢 [Happy] Forgot → Reset → Login with new password succeeds")
    void should_forgot_then_reset_then_login_with_new_password() {
        String email = "reset-happy@example.com";
        registerAndVerify(email);

        assertThat(loginStatus(email, STRONG_PASSWORD)).isEqualTo(200);

        inMemoryEmailAdapter().clear();
        ResponseEntity<ApiResponse<AuthMessageResponse>> forgotResp = restClient().post()
                .uri("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ForgotPasswordRequest(email))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {})
                .toEntity(new ParameterizedTypeReference<ApiResponse<AuthMessageResponse>>() {});

        assertThat(forgotResp.getStatusCode().value()).isEqualTo(200);
        assertThat(forgotResp.getBody().isSuccess()).isTrue();

        String resetLink = latestResetLink(email);
        String token = extractResetToken(resetLink);
        assertThat(token).isNotBlank();

        ResponseEntity<ApiResponse<AuthMessageResponse>> resetResp = restClient().post()
                .uri("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ResetPasswordRequest(token, NEW_PASSWORD))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {})
                .toEntity(new ParameterizedTypeReference<ApiResponse<AuthMessageResponse>>() {});

        assertThat(resetResp.getStatusCode().value()).isEqualTo(200);
        assertThat(resetResp.getBody().isSuccess()).isTrue();

        assertThat(loginStatus(email, NEW_PASSWORD)).isEqualTo(200);

        assertThat(loginStatus(email, STRONG_PASSWORD)).isIn(400, 401);
    }

    @Test
    @DisplayName("🔴 [Error] Reset with invalid/tampered token is rejected")
    void should_reject_reset_when_token_invalid() {
        String email = "reset-bad-token@example.com";
        registerAndVerify(email);

        ResponseEntity<ApiResponse<AuthMessageResponse>> resetResp = restClient().post()
                .uri("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ResetPasswordRequest("totally-invalid-fake-token-abc123", NEW_PASSWORD))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {})
                .toEntity(new ParameterizedTypeReference<ApiResponse<AuthMessageResponse>>() {});

        assertThat(resetResp.getStatusCode().value()).isIn(400, 401);
        assertThat(resetResp.getBody().isSuccess()).isFalse();
    }
}
