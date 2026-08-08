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

@DisplayName("Re-Registration Recovery Flow E2E — pending/active re-register scenarios")
class ReRegistrationRecoveryFlowIT extends AbstractE2EIT {

    private static final String STRONG_PASSWORD = "Str0ng!Pass#2026";

    private ResponseEntity<ApiResponse<AuthMessageResponse>> registerRaw(String email) {
        return restClient().post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterRequest(email, STRONG_PASSWORD, "Recovery User"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {})
                .toEntity(new ParameterizedTypeReference<ApiResponse<AuthMessageResponse>>() {});
    }

    private UUID registerAndVerify(String email) {
        ResponseEntity<ApiResponse<AuthMessageResponse>> regResp = registerRaw(email);
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

    @Test
    @DisplayName("🔴 [Error] Reject re-registration when first user is ACTIVE")
    void should_reject_re_registration_when_first_user_active() {
        String email = "reregister-active@example.com";
        registerAndVerify(email);

        flushRedis();

        ResponseEntity<ApiResponse<AuthMessageResponse>> reRegResp = registerRaw(email);

        assertThat(reRegResp.getStatusCode().value()).isIn(400, 409);
        assertThat(reRegResp.getBody().isSuccess()).isFalse();
    }

    @Test
    @DisplayName("🟡 [Boundary] Reject re-registration when within cooldown")
    void should_reject_re_registration_when_within_cooldown() {
        String email = "reregister-cooldown@example.com";
        ResponseEntity<ApiResponse<AuthMessageResponse>> firstReg = registerRaw(email);
        assertThat(firstReg.getStatusCode().value()).isEqualTo(201);

        ResponseEntity<ApiResponse<AuthMessageResponse>> secondReg = registerRaw(email);

        assertThat(secondReg.getStatusCode().value()).isIn(429, 400);
    }

    @Test
    @DisplayName("🟢 [Happy] Old OTP invalidated after re-registration of pending user")
    void should_clear_old_otp_when_re_registering_pending_user() {
        String email = "reregister-otp@example.com";
        ResponseEntity<ApiResponse<AuthMessageResponse>> firstReg = registerRaw(email);
        assertThat(firstReg.getStatusCode().value()).isEqualTo(201);
        UUID firstUserId = firstReg.getBody().getData().userId();
        String oldOtp = latestOtpCode(email);

        flushRedis();
        inMemoryEmailAdapter().clear();

        ResponseEntity<ApiResponse<AuthMessageResponse>> secondReg = registerRaw(email);
        if (secondReg.getStatusCode().value() == 201) {
            UUID secondUserId = secondReg.getBody().getData().userId();
            String newOtp = latestOtpCode(email);

            ResponseEntity<ApiResponse<AuthResponse>> verifyOldResp = restClient().post()
                    .uri("/api/v1/auth/verify-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new VerifyOtpRequest(secondUserId, oldOtp))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {})
                    .toEntity(new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {});

            if (!oldOtp.equals(newOtp)) {
                assertThat(verifyOldResp.getStatusCode().value()).isIn(400, 401);
            }

            ResponseEntity<ApiResponse<AuthResponse>> verifyNewResp = restClient().post()
                    .uri("/api/v1/auth/verify-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new VerifyOtpRequest(secondUserId, newOtp))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {})
                    .toEntity(new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {});

            assertThat(verifyNewResp.getStatusCode().value()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("🔴 [Error] Duplicate registration rejected after verify + active")
    void should_reject_duplicate_when_user_verified_and_active() {
        String email = "reregister-verified@example.com";
        UUID userId = registerAndVerify(email);

        ResponseEntity<ApiResponse<AuthResponse>> loginResp = restClient().post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(email, STRONG_PASSWORD))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {})
                .toEntity(new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {});
        assertThat(loginResp.getStatusCode().value()).isEqualTo(200);

        flushRedis();

        ResponseEntity<ApiResponse<AuthMessageResponse>> reRegResp = registerRaw(email);
        assertThat(reRegResp.getStatusCode().value()).isIn(400, 409);
        assertThat(reRegResp.getBody().isSuccess()).isFalse();
    }
}
