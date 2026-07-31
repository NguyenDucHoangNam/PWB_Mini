package com.pwb.iam.e2e;

import com.pwb.iam.api.dto.request.LoginRequest;
import com.pwb.iam.api.dto.request.RegisterRequest;
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

@DisplayName("Account Lockout Flow E2E — lock after N failed logins")
class AccountLockoutFlowIT extends AbstractE2EIT {

    private static final String STRONG_PASSWORD = "Str0ng!Pass#2026";
    private static final String WRONG_PASSWORD = "WrongPass!1234";

    private UUID registerAndVerify(String email) {
        ResponseEntity<ApiResponse<AuthMessageResponse>> regResp = restClient().post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterRequest(email, STRONG_PASSWORD, "Lockout User"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {})
                .toEntity(new ParameterizedTypeReference<ApiResponse<AuthMessageResponse>>() {});

        assertThat(regResp.getStatusCode().value()).isEqualTo(201);
        UUID userId = regResp.getBody().getData().userId();

        String otp = latestOtpCode(email);
        restClient().post()
                .uri("/api/v1/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new VerifyOtpRequest(userId, otp))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {})
                .toEntity(new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {});

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
    @DisplayName("🟡 [Boundary] Lock account after 5 failed login attempts")
    void should_lock_account_after_max_failed_logins() {
        String email = "lockout@example.com";
        registerAndVerify(email);

        for (int i = 0; i < 5; i++) {
            int status = loginStatus(email, WRONG_PASSWORD);
            assertThat(status).isIn(400, 401);
        }

        int lockedStatus = loginStatus(email, STRONG_PASSWORD);
        assertThat(lockedStatus).isIn(429, 423);
    }

    @Test
    @DisplayName("🟢 [Happy] Reset attempts after successful login mid-sequence")
    void should_reset_attempts_after_successful_login() {
        String email = "lockout-reset@example.com";
        registerAndVerify(email);

        for (int i = 0; i < 4; i++) {
            loginStatus(email, WRONG_PASSWORD);
        }

        int successStatus = loginStatus(email, STRONG_PASSWORD);
        assertThat(successStatus).isEqualTo(200);

        for (int i = 0; i < 4; i++) {
            loginStatus(email, WRONG_PASSWORD);
        }

        int afterResetStatus = loginStatus(email, STRONG_PASSWORD);
        assertThat(afterResetStatus).isEqualTo(200);
    }
}
