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

@DisplayName("Auth Flow E2E — full register/verify/login flow")
class AuthFlowE2EIT extends AbstractE2EIT {

    private static final String STRONG_PASSWORD = "Str0ng!Pass#2026";

    private UUID register(String email) {
        ResponseEntity<ApiResponse<AuthMessageResponse>> response = restClient().post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterRequest(email, STRONG_PASSWORD, "Test User"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {})
                .toEntity(new ParameterizedTypeReference<ApiResponse<AuthMessageResponse>>() {});

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isNotNull();
        return response.getBody().getData().userId();
    }

    private AuthResponse verifyOtp(UUID userId, String code) {
        return verifyOtpEntity(userId, code).getBody().getData();
    }

    /**
     * Same call as {@link #verifyOtp}, but hands back the whole response so a caller can reach the
     * refresh cookie — the token is not in the body any more.
     */
    private ResponseEntity<ApiResponse<AuthResponse>> verifyOtpEntity(UUID userId, String code) {
        ResponseEntity<ApiResponse<AuthResponse>> response = restClient().post()
                .uri("/api/v1/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new VerifyOtpRequest(userId, code))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {})
                .toEntity(new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {});

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData().accessToken()).isNotBlank();
        return response;
    }

    /** See the equivalent note in {@code RefreshTokenRotationIT}: httpOnly cookie, not body field. */
    private static String refreshCookieOf(ResponseEntity<?> response) {
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).isNotNull();
        return setCookies.stream()
                .filter(c -> c.startsWith("pwb_refresh_token="))
                .map(c -> c.substring("pwb_refresh_token=".length(), c.indexOf(';')))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no pwb_refresh_token cookie on the response"));
    }

    private AuthResponse login(String email, String password) {
        ResponseEntity<ApiResponse<AuthResponse>> response = restClient().post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(email, password))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {})
                .toEntity(new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {});

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().isSuccess()).isTrue();
        return response.getBody().getData();
    }

    private int loginStatus(String email, String password) {
        ResponseEntity<ApiResponse<AuthResponse>> response = restClient().post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(email, password))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {})
                .toEntity(new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {});
        return response.getStatusCode().value();
    }

    @Test
    @DisplayName("should_register_then_verify_then_login")
    void should_register_then_verify_then_login() {
        String email = "alice@example.com";
        UUID userId = register(email);
        assertThat(userId).isNotNull();

        String otp = latestOtpCode(email);
        AuthResponse verifyResponse = verifyOtp(userId, otp);
        assertThat(verifyResponse.status()).isEqualTo("ACTIVE");

        AuthResponse loginResponse = login(email, STRONG_PASSWORD);
        assertThat(loginResponse.accessToken()).isNotBlank();
        assertThat(loginResponse.status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("should_register_then_login_after_verification")
    void should_register_then_login_after_verification() {
        String email = "carol@example.com";
        UUID userId = register(email);
        String otp = latestOtpCode(email);
        verifyOtp(userId, otp);

        AuthResponse loginResponse = login(email, STRONG_PASSWORD);
        assertThat(loginResponse.status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("should_fail_login_when_not_verified_yet")
    void should_fail_login_when_not_verified_yet() {
        String email = "bob@example.com";
        register(email);

        int status = loginStatus(email, STRONG_PASSWORD);
        assertThat(status).isIn(400, 401, 403);
    }

    @Test
    @DisplayName("should_emit_otp_email_on_register")
    void should_emit_otp_email_on_register() {
        String email = "dave@example.com";
        UUID userId = register(email);

        assertThat(userId).isNotNull();
        assertThat(inMemoryEmailAdapter().sentEmails()).hasSize(1);
        String otp = latestOtpCode(email);
        assertThat(otp).isNotBlank();
        assertThat(otp).hasSizeBetween(4, 10);
    }

    @Test
    @DisplayName("should_reject_duplicate_email_registration")
    void should_reject_duplicate_email_registration() {
        String email = "eve@example.com";
        UUID userId = register(email);
        String otp = latestOtpCode(email);
        verifyOtp(userId, otp);

        flushRedis();

        ResponseEntity<ApiResponse<AuthMessageResponse>> response = restClient().post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterRequest(email, STRONG_PASSWORD, "Duplicate"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {})
                .toEntity(new ParameterizedTypeReference<ApiResponse<AuthMessageResponse>>() {});

        assertThat(response.getStatusCode().value()).isIn(400, 409);
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    @Test
    @DisplayName("should_rotate_refresh_token")
    void should_rotate_refresh_token() {
        String email = "frank@example.com";
        UUID userId = register(email);
        String otp = latestOtpCode(email);
        ResponseEntity<ApiResponse<AuthResponse>> verifyResponse = verifyOtpEntity(userId, otp);

        ResponseEntity<ApiResponse<AuthResponse>> refreshResponse = restClient().post()
                .uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.COOKIE, "pwb_refresh_token=" + refreshCookieOf(verifyResponse))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {})
                .toEntity(new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {});

        assertThat(refreshResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(refreshResponse.getBody().isSuccess()).isTrue();
        assertThat(refreshResponse.getBody().getData().accessToken()).isNotBlank();
        assertThat(refreshCookieOf(refreshResponse)).isNotEqualTo(refreshCookieOf(verifyResponse));
    }
}