package com.pwb.iam.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.pwb.iam.testsupport.AuthControllerHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;

import java.util.Map;

import static com.pwb.iam.testsupport.AuthControllerHarness.COOKIE_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Pins the wire format the frontend reads, separately from the behaviour in {@code AuthControllerTest}.
 *
 * <p>The rule worth stating out loud: the refresh token reaches the client only as an HttpOnly
 * cookie. Putting it back into the JSON body would break no other test here and would hand it to
 * any script on the page, so every token-issuing endpoint asserts both halves — present in
 * {@code Set-Cookie}, absent from {@code data}.
 */
@DisplayName("auth endpoint contract")
class AuthEndpointContractTest {

    private AuthControllerHarness harness;

    @BeforeEach
    void setUp() {
        harness = new AuthControllerHarness();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private JsonNode bodyOf(MvcResult result) throws Exception {
        return harness.objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void assertEnvelope(JsonNode json) {
        assertThat(json.has("success")).isTrue();
        assertThat(json.has("data")).isTrue();
        assertThat(json.has("traceId")).isTrue();
        assertThat(json.has("timestamp")).isTrue();
        assertThat(json.get("success").asBoolean()).isTrue();
    }

    private void assertTokenPayload(MvcResult result) throws Exception {
        JsonNode data = bodyOf(result).get("data");
        assertThat(data.has("accessToken")).isTrue();
        assertThat(data.has("tokenType")).isTrue();
        assertThat(data.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(data.has("expiresIn")).isTrue();
        assertThat(data.has("userId")).isTrue();
        assertThat(data.has("email")).isTrue();
        assertThat(data.has("status")).isTrue();
        assertThat(data.has("role")).isTrue();

        assertThat(data.has("refreshToken"))
                .as("the refresh token must reach the client only as an HttpOnly cookie")
                .isFalse();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).contains(COOKIE_NAME + "=refresh-token-stub");
        assertThat(setCookie).contains("HttpOnly");
    }

    @Test
    @DisplayName("POST /register returns the envelope with a user id and a message")
    void register_contract() throws Exception {
        MvcResult result = harness.mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(harness.json(Map.of(
                                "email", "user@example.com",
                                "password", "StrongPass1!@#$",
                                "fullName", "Alice"))))
                .andReturn();

        JsonNode json = bodyOf(result);
        assertEnvelope(json);
        assertThat(json.get("data").has("userId")).isTrue();
        assertThat(json.get("data").has("message")).isTrue();
        assertThat(json.get("data").get("userId").asText()).isEqualTo(harness.userId.toString());
    }

    @Test
    @DisplayName("POST /verify-otp answers with tokens")
    void verify_otp_contract() throws Exception {
        MvcResult result = harness.mockMvc.perform(post("/api/v1/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(harness.json(Map.of(
                                "userId", harness.userId.toString(),
                                "code", "123456"))))
                .andReturn();

        assertEnvelope(bodyOf(result));
        assertTokenPayload(result);
    }

    @Test
    @DisplayName("POST /login answers with tokens")
    void login_contract() throws Exception {
        MvcResult result = harness.mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(harness.json(Map.of(
                                "email", "active@example.com",
                                "password", "StrongPass1!@#$")))
                        .header("User-Agent", "TestAgent"))
                .andReturn();

        assertEnvelope(bodyOf(result));
        assertTokenPayload(result);
    }

    @Test
    @DisplayName("POST /refresh takes the token from the cookie and answers with tokens")
    void refresh_contract() throws Exception {
        MvcResult result = harness.mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(COOKIE_NAME, "refresh.jwt")))
                .andReturn();

        assertEnvelope(bodyOf(result));
        assertTokenPayload(result);
    }

    @Test
    @DisplayName("POST /google-login answers with tokens")
    void google_login_contract() throws Exception {
        MvcResult result = harness.mockMvc.perform(post("/api/v1/auth/google-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(harness.json(Map.of("idToken", "valid-google-id-token-string")))
                        .header("Accept-Language", "vi"))
                .andReturn();

        assertEnvelope(bodyOf(result));
        assertTokenPayload(result);
    }

    @Test
    @DisplayName("POST /logout returns the user id and a message, and expires the cookie")
    void logout_contract() throws Exception {
        harness.authenticate();
        MvcResult result = harness.mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(harness.json(Map.of(
                                "refreshToken", "refresh.jwt",
                                "accessJti", "jti-1",
                                "accessExpiresInSeconds", 900))))
                .andReturn();

        JsonNode json = bodyOf(result);
        assertEnvelope(json);
        assertThat(json.get("data").get("userId").asText()).isEqualTo(harness.userId.toString());
        assertThat(json.get("data").has("message")).isTrue();
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
    }

    @Test
    @DisplayName("POST /forgot-password returns a message and no user id")
    void forgot_password_contract() throws Exception {
        MvcResult result = harness.mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(harness.json(Map.of("email", "active@example.com")))
                        .header("User-Agent", "TestAgent"))
                .andReturn();

        JsonNode json = bodyOf(result);
        assertEnvelope(json);
        assertThat(json.get("data").has("message")).isTrue();
        // Echoing the id back would turn this into a way to ask whether an address is registered.
        JsonNode userIdNode = json.get("data").get("userId");
        assertThat(userIdNode == null || userIdNode.isNull()).isTrue();
    }

    @Test
    @DisplayName("POST /reset-password returns the user id and a message")
    void reset_password_contract() throws Exception {
        MvcResult result = harness.mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(harness.json(Map.of(
                                "token", "valid-token",
                                "newPassword", "NewPass1!@#$"))))
                .andReturn();

        JsonNode json = bodyOf(result);
        assertEnvelope(json);
        assertThat(json.get("data").get("userId").asText()).isEqualTo(harness.userId.toString());
        assertThat(json.get("data").has("message")).isTrue();
    }

    @Test
    @DisplayName("POST /change-password returns the user id and a message")
    void change_password_contract() throws Exception {
        harness.authenticate();
        MvcResult result = harness.mockMvc.perform(post("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(harness.json(Map.of(
                                "currentPassword", "OldPass1!@#$",
                                "newPassword", "NewPass1!@#$"))))
                .andReturn();

        JsonNode json = bodyOf(result);
        assertEnvelope(json);
        assertThat(json.get("data").get("userId").asText()).isEqualTo(harness.userId.toString());
        assertThat(json.get("data").has("message")).isTrue();
    }

    @Test
    @DisplayName("POST /resend-otp returns the user id and a message")
    void resend_otp_contract() throws Exception {
        MvcResult result = harness.mockMvc.perform(post("/api/v1/auth/resend-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(harness.json(Map.of("userId", harness.userId.toString()))))
                .andReturn();

        JsonNode json = bodyOf(result);
        assertEnvelope(json);
        assertThat(json.get("data").get("userId").asText()).isEqualTo(harness.userId.toString());
        assertThat(json.get("data").has("message")).isTrue();
    }
}
