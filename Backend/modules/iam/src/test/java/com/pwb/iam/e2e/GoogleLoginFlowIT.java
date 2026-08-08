package com.pwb.iam.e2e;

import com.pwb.iam.api.dto.request.GoogleLoginRequest;
import com.pwb.iam.api.dto.request.RegisterRequest;
import com.pwb.iam.api.dto.request.VerifyOtpRequest;
import com.pwb.iam.api.dto.response.AuthMessageResponse;
import com.pwb.iam.api.dto.response.AuthResponse;
import com.pwb.iam.domain.model.GoogleUserInfo;
import com.pwb.iam.testsupport.AbstractE2EIT;
import com.pwb.shared.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Google Login Flow E2E — new/existing/link scenarios")
class GoogleLoginFlowIT extends AbstractE2EIT {

    private static final String STRONG_PASSWORD = "Str0ng!Pass#2026";
    private static final String DUMMY_ID_TOKEN = "eyJhbGciOiJSUzI1NiJ9.dummy-google-token-for-e2e-test";

    @BeforeEach
    void setUpGoogleStub() {
        googleTokenVerifier().presetPayload(null);
    }

    private ResponseEntity<ApiResponse<AuthResponse>> googleLogin(String idToken) {
        return restClient().post()
                .uri("/api/v1/auth/google-login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new GoogleLoginRequest(idToken))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {})
                .toEntity(new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {});
    }

    private UUID registerAndVerifyLocal(String email) {
        ResponseEntity<ApiResponse<AuthMessageResponse>> regResp = restClient().post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterRequest(email, STRONG_PASSWORD, "Local User"))
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

    @Test
    @DisplayName("🟢 [Happy] Login with new Google account creates user")
    void should_login_with_new_google_account() {
        GoogleUserInfo payload = new GoogleUserInfo(
                "google-new-sub-001", "newgoogle@example.com", true, "New Google User", "https://photo.url/pic.jpg"
        );
        googleTokenVerifier().presetPayload(payload);

        ResponseEntity<ApiResponse<AuthResponse>> resp = googleLogin(DUMMY_ID_TOKEN);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody().isSuccess()).isTrue();
        AuthResponse data = resp.getBody().getData();
        assertThat(data.accessToken()).isNotBlank();
        assertThat(data.email()).isEqualTo("newgoogle@example.com");
        assertThat(data.status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("🟢 [Happy] Link existing local account to Google")
    void should_link_existing_local_account_to_google() {
        String email = "linkable@example.com";
        registerAndVerifyLocal(email);

        GoogleUserInfo payload = new GoogleUserInfo(
                "google-link-sub-002", email, true, "Linked User", "https://photo.url/linked.jpg"
        );
        googleTokenVerifier().presetPayload(payload);

        ResponseEntity<ApiResponse<AuthResponse>> resp = googleLogin(DUMMY_ID_TOKEN);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody().isSuccess()).isTrue();
        AuthResponse data = resp.getBody().getData();
        assertThat(data.accessToken()).isNotBlank();
        assertThat(data.email()).isEqualTo(email);
        assertThat(data.status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("🟢 [Happy] Login existing Google user returns tokens")
    void should_login_existing_google_user() {
        GoogleUserInfo payload = new GoogleUserInfo(
                "google-existing-sub-003", "existinggoogle@example.com", true, "Existing Google", "https://photo.url/exist.jpg"
        );
        googleTokenVerifier().presetPayload(payload);

        ResponseEntity<ApiResponse<AuthResponse>> firstLogin = googleLogin(DUMMY_ID_TOKEN);
        assertThat(firstLogin.getStatusCode().value()).isEqualTo(200);

        ResponseEntity<ApiResponse<AuthResponse>> secondLogin = googleLogin(DUMMY_ID_TOKEN);

        assertThat(secondLogin.getStatusCode().value()).isEqualTo(200);
        assertThat(secondLogin.getBody().isSuccess()).isTrue();
        AuthResponse data = secondLogin.getBody().getData();
        assertThat(data.accessToken()).isNotBlank();
        assertThat(data.status()).isEqualTo("ACTIVE");
    }
}
