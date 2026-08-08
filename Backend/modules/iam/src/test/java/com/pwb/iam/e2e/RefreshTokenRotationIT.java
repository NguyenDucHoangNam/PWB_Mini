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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Refresh Token Rotation E2E — rotate and invalidate old token")
class RefreshTokenRotationIT extends AbstractE2EIT {

    private static final String STRONG_PASSWORD = "Str0ng!Pass#2026";
    private static final String REFRESH_COOKIE = "pwb_refresh_token";

    /**
     * Pulls the refresh token out of the response's Set-Cookie header.
     *
     * <p>It is no longer in the body. The endpoint sets it as an httpOnly cookie precisely so that
     * page scripts cannot read it, which means a client — including this one — can only ever echo
     * the cookie back, never inspect the value on its own terms.
     */
    private static String refreshCookieOf(ResponseEntity<?> response) {
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).isNotNull();
        return setCookies.stream()
                .filter(c -> c.startsWith(REFRESH_COOKIE + "="))
                .map(c -> c.substring((REFRESH_COOKIE + "=").length(), c.indexOf(';')))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + REFRESH_COOKIE + " cookie on the response"));
    }

    private ResponseEntity<ApiResponse<AuthResponse>> registerVerifyAndLogin(String email) {
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
        return loginResp;
    }

    /** Sends the refresh token the only way a browser can: back as the cookie it was issued as. */
    private ResponseEntity<ApiResponse<AuthResponse>> refresh(String refreshCookieValue) {
        return restClient().post()
                .uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.COOKIE, REFRESH_COOKIE + "=" + refreshCookieValue)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {})
                .toEntity(new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {});
    }

    @Test
    @DisplayName("🟢 [Happy] Rotate refresh token → old token invalidated")
    void should_rotate_refresh_token_and_invalidate_old() {
        String email = "rotation@example.com";
        String oldRefreshToken = refreshCookieOf(registerVerifyAndLogin(email));
        assertThat(oldRefreshToken).isNotBlank();

        ResponseEntity<ApiResponse<AuthResponse>> rotateResp = refresh(oldRefreshToken);
        assertThat(rotateResp.getStatusCode().value()).isEqualTo(200);
        assertThat(rotateResp.getBody().isSuccess()).isTrue();
        assertThat(rotateResp.getBody().getData().accessToken()).isNotBlank();

        String rotatedRefreshToken = refreshCookieOf(rotateResp);
        assertThat(rotatedRefreshToken).isNotEqualTo(oldRefreshToken);

        // Replaying the retired token is treated as theft, not as an ordinary expiry: the adapter
        // remembers it for the rest of its lifetime and drops every session for the account.
        ResponseEntity<ApiResponse<AuthResponse>> replayResp = refresh(oldRefreshToken);
        assertThat(replayResp.getStatusCode().value()).isIn(400, 401);
    }
}
