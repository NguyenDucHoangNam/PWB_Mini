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

@DisplayName("Account Lockout Recovery E2E — recovery from lockout state")
class AccountLockoutRecoveryIT extends AbstractE2EIT {

    private static final String STRONG_PASSWORD = "Str0ng!Pass#2026";
    private static final String WRONG_PASSWORD = "WrongPass!1234";

    private UUID registerAndVerify(String email) {
        ResponseEntity<ApiResponse<AuthMessageResponse>> regResp = restClient().post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterRequest(email, STRONG_PASSWORD, "Recovery User"))
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

    private ResponseEntity<ApiResponse<AuthResponse>> loginRaw(String email, String password) {
        return restClient().post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(email, password))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {})
                .toEntity(new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {});
    }

    private int loginStatus(String email, String password) {
        return loginRaw(email, password).getStatusCode().value();
    }

    @Test
    @DisplayName("🔵 [Side Effect] Lockout response contains error metadata")
    void should_show_remaining_lockout_time_in_response() {
        String email = "lockout-meta@example.com";
        registerAndVerify(email);

        for (int i = 0; i < 5; i++) {
            loginStatus(email, WRONG_PASSWORD);
        }

        ResponseEntity<ApiResponse<AuthResponse>> lockedResp = loginRaw(email, STRONG_PASSWORD);

        assertThat(lockedResp.getStatusCode().value()).isIn(429, 423);
        assertThat(lockedResp.getBody().isSuccess()).isFalse();
        assertThat(lockedResp.getBody().getCode()).isNotBlank();
    }

    @Test
    @DisplayName("🟢 [Happy] Successful login between failures prevents lockout")
    void should_not_count_successful_login_as_failure() {
        String email = "lockout-interleave@example.com";
        registerAndVerify(email);

        for (int i = 0; i < 4; i++) {
            loginStatus(email, WRONG_PASSWORD);
        }
        assertThat(loginStatus(email, STRONG_PASSWORD)).isEqualTo(200);

        for (int i = 0; i < 4; i++) {
            loginStatus(email, WRONG_PASSWORD);
        }
        assertThat(loginStatus(email, STRONG_PASSWORD)).isEqualTo(200);
    }

    @Test
    @DisplayName("🟣 [Recovery] Login succeeds after flushing Redis lockout state")
    void should_recover_login_after_lockout_by_resetting_redis() {
        String email = "lockout-flush@example.com";
        registerAndVerify(email);

        for (int i = 0; i < 5; i++) {
            loginStatus(email, WRONG_PASSWORD);
        }

        assertThat(loginStatus(email, STRONG_PASSWORD)).isIn(429, 423);

        flushRedis();

        assertThat(loginStatus(email, STRONG_PASSWORD)).isEqualTo(200);
    }
}
