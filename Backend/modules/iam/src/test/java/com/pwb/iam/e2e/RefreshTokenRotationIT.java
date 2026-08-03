package com.pwb.iam.e2e;

import com.pwb.iam.api.dto.request.LoginRequest;
import com.pwb.iam.api.dto.request.RefreshTokenRequest;
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

@DisplayName("Refresh Token Rotation E2E — rotate and invalidate old token")
class RefreshTokenRotationIT extends AbstractE2EIT {

    private static final String STRONG_PASSWORD = "Str0ng!Pass#2026";

    private AuthResponse registerVerifyAndLogin(String email) {
        ResponseEntity<ApiResponse<AuthMessageResponse>> regResp = restClient().post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterRequest(email, STRONG_PASSWORD, "Rotation User"))
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

        ResponseEntity<ApiResponse<AuthResponse>> loginResp = restClient().post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(email, STRONG_PASSWORD))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {})
                .toEntity(new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {});

        assertThat(loginResp.getStatusCode().value()).isEqualTo(200);
        return loginResp.getBody().getData();
    }

    private ResponseEntity<ApiResponse<AuthResponse>> refresh(String refreshToken) {
        return restClient().post()
                .uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RefreshTokenRequest(refreshToken))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {})
                .toEntity(new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {});
    }

    @Test
    @DisplayName("🟢 [Happy] Rotate refresh token → old token invalidated")
    void should_rotate_refresh_token_and_invalidate_old() {
        String email = "rotation@example.com";
        AuthResponse loginResult = registerVerifyAndLogin(email);
        String oldRefreshToken = loginResult.getRefreshToken();
        assertThat(oldRefreshToken).isNotBlank();

        ResponseEntity<ApiResponse<AuthResponse>> rotateResp = refresh(oldRefreshToken);
        assertThat(rotateResp.getStatusCode().value()).isEqualTo(200);
        assertThat(rotateResp.getBody().isSuccess()).isTrue();

        AuthResponse rotated = rotateResp.getBody().getData();
        assertThat(rotated.getAccessToken()).isNotBlank();
        assertThat(rotated.getRefreshToken()).isNotBlank();
        assertThat(rotated.getRefreshToken()).isNotEqualTo(oldRefreshToken);

        ResponseEntity<ApiResponse<AuthResponse>> replayResp = refresh(oldRefreshToken);
        assertThat(replayResp.getStatusCode().value()).isIn(401, 400, 500);
    }
}
