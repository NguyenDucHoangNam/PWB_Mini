package com.pwb.iam.e2e;

import com.pwb.iam.api.dto.request.RegisterRequest;
import com.pwb.iam.api.dto.request.ResendOtpRequest;
import com.pwb.iam.api.dto.request.VerifyOtpRequest;
import com.pwb.iam.api.dto.response.AuthMessageResponse;
import com.pwb.iam.api.dto.response.AuthResponse;
import com.pwb.iam.domain.model.OtpPurpose;
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

@DisplayName("OTP Resend Limit Flow E2E — cooldown and daily limit")
class OtpResendLimitFlowIT extends AbstractE2EIT {

    private static final String STRONG_PASSWORD = "Str0ng!Pass#2026";

    private UUID register(String email) {
        ResponseEntity<ApiResponse<AuthMessageResponse>> regResp = restClient().post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterRequest(email, STRONG_PASSWORD, "OTP User"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {})
                .toEntity(new ParameterizedTypeReference<ApiResponse<AuthMessageResponse>>() {});

        assertThat(regResp.getStatusCode().value()).isEqualTo(201);
        return regResp.getBody().getData().userId();
    }

    private int resendOtpStatus(UUID userId) {
        ResponseEntity<ApiResponse<AuthMessageResponse>> resp = restClient().post()
                .uri("/api/v1/auth/resend-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ResendOtpRequest(userId, OtpPurpose.REGISTER))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp2) -> {})
                .toEntity(new ParameterizedTypeReference<ApiResponse<AuthMessageResponse>>() {});
        return resp.getStatusCode().value();
    }

    @Test
    @DisplayName("🟡 [Boundary] Throttle resend OTP when cooldown active")
    void should_throttle_resend_otp_within_cooldown() throws InterruptedException {
        String email = "otp-cooldown@example.com";
        UUID userId = register(email);

        Thread.sleep(1100);
        int firstResend = resendOtpStatus(userId);
        assertThat(firstResend).isEqualTo(200);

        int secondResend = resendOtpStatus(userId);
        assertThat(secondResend).isIn(429, 400);
    }

    @Test
    @DisplayName("🟡 [Boundary] Reject resend when daily limit exceeded")
    void should_throw_daily_limit_when_exceeded() throws InterruptedException {
        String email = "otp-daily@example.com";
        UUID userId = register(email);

        Thread.sleep(1100);
        int firstResend = resendOtpStatus(userId);
        assertThat(firstResend).isEqualTo(200);

        Thread.sleep(1100);
        int secondResend = resendOtpStatus(userId);
        assertThat(secondResend).isIn(200, 429);

        Thread.sleep(1100);
        int exceededStatus = resendOtpStatus(userId);
        assertThat(exceededStatus).isIn(429, 400);
    }
}
