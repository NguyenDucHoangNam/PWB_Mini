package com.pwb.iam.infrastructure.security.jwt;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.model.GoogleUserInfo;
import com.pwb.iam.domain.service.GoogleTokenVerifierPort;
import com.pwb.iam.infrastructure.config.GoogleProperties;
import com.pwb.iam.testsupport.AbstractUnitIT;
import com.pwb.shared.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;

@DisplayName("GoogleTokenVerifierAdapter — Unit IT (mocked delegate)")
class GoogleTokenVerifierAdapterIT extends AbstractUnitIT {

    private GoogleTokenVerifierAdapter adapter;
    private GoogleIdTokenVerifier mockDelegate;

    @BeforeEach
    void setUp() throws Exception {
        GoogleProperties props = new GoogleProperties();
        props.setClientId("test-google-client-id.apps.googleusercontent.com");
        props.setClockSkewSeconds(30L);

        adapter = new GoogleTokenVerifierAdapter(props);
        mockDelegate = Mockito.mock(GoogleIdTokenVerifier.class);
        ReflectionTestUtils.setField(adapter, "delegate", mockDelegate);
    }

    @AfterEach
    void tearDown() {
        Mockito.reset(mockDelegate);
    }

    private GoogleIdToken mockIdToken(String sub, String email, boolean emailVerified,
                                     String issuer, String name, String picture) {
        GoogleIdToken mockToken = Mockito.mock(GoogleIdToken.class);
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject(sub);
        payload.setEmail(email);
        payload.setEmailVerified(emailVerified);
        payload.setIssuer(issuer);
        if (name != null) payload.set("name", name);
        if (picture != null) payload.set("picture", picture);
        Mockito.when(mockToken.getPayload()).thenReturn(payload);
        return mockToken;
    }

    @Test
    @DisplayName("verify_returns_user_info_for_valid_token")
    void verify_returns_user_info_for_valid_token() throws Exception {
        GoogleIdToken token = mockIdToken(
                "google-sub-123",
                "user@gmail.com",
                true,
                "accounts.google.com",
                "Test User",
                "https://example.com/avatar.png");
        Mockito.when(mockDelegate.verify(anyString())).thenReturn(token);

        GoogleUserInfo info = adapter.verify("dummy-id-token");

        assertThat(info).isNotNull();
        assertThat(info.sub()).isEqualTo("google-sub-123");
        assertThat(info.email()).isEqualTo("user@gmail.com");
        assertThat(info.verified()).isTrue();
        assertThat(info.name()).isEqualTo("Test User");
        assertThat(info.picture()).isEqualTo("https://example.com/avatar.png");
    }

    @Test
    @DisplayName("verify_lowercases_and_trims_email")
    void verify_lowercases_and_trims_email() throws Exception {
        GoogleIdToken token = mockIdToken(
                "sub-1", "  User@Gmail.COM  ", true,
                "accounts.google.com", null, null);
        Mockito.when(mockDelegate.verify(anyString())).thenReturn(token);

        GoogleUserInfo info = adapter.verify("dummy");

        assertThat(info.email()).isEqualTo("user@gmail.com");
    }

    @Test
    @DisplayName("verify_accepts_https_issuer")
    void verify_accepts_https_issuer() throws Exception {
        GoogleIdToken token = mockIdToken(
                "sub-2", "user@gmail.com", true,
                "https://accounts.google.com", null, null);
        Mockito.when(mockDelegate.verify(anyString())).thenReturn(token);

        GoogleUserInfo info = adapter.verify("dummy");

        assertThat(info.sub()).isEqualTo("sub-2");
    }

    @Test
    @DisplayName("verify_throws_when_delegate_returns_null")
    void verify_throws_when_delegate_returns_null() throws Exception {
        Mockito.when(mockDelegate.verify(anyString())).thenReturn(null);

        assertThatThrownBy(() -> adapter.verify("invalid-token"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode.code", IamErrorCode.AUTH_GOOGLE_TOKEN_INVALID.code());
    }

    @Test
    @DisplayName("verify_throws_when_delegate_throws_GeneralSecurityException")
    void verify_throws_when_delegate_throws_GeneralSecurityException() throws Exception {
        Mockito.when(mockDelegate.verify(anyString()))
                .thenThrow(new GeneralSecurityException("signature invalid"));

        assertThatThrownBy(() -> adapter.verify("bad-token"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode.code", IamErrorCode.AUTH_GOOGLE_TOKEN_INVALID.code());
    }

    @Test
    @DisplayName("verify_throws_when_delegate_throws_IOException")
    void verify_throws_when_delegate_throws_IOException() throws Exception {
        Mockito.when(mockDelegate.verify(anyString()))
                .thenThrow(new IOException("network error"));

        assertThatThrownBy(() -> adapter.verify("bad-token"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode.code", IamErrorCode.AUTH_GOOGLE_TOKEN_INVALID.code());
    }

    @Test
    @DisplayName("verify_throws_when_issuer_not_google")
    void verify_throws_when_issuer_not_google() throws Exception {
        GoogleIdToken token = mockIdToken(
                "sub-3", "user@gmail.com", true,
                "evil-issuer.com", null, null);
        Mockito.when(mockDelegate.verify(anyString())).thenReturn(token);

        assertThatThrownBy(() -> adapter.verify("bad-token"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode.code", IamErrorCode.AUTH_GOOGLE_TOKEN_INVALID.code());
    }

    @Test
    @DisplayName("verify_throws_when_issuer_null")
    void verify_throws_when_issuer_null() throws Exception {
        GoogleIdToken token = mockIdToken(
                "sub-4", "user@gmail.com", true,
                null, null, null);
        Mockito.when(mockDelegate.verify(anyString())).thenReturn(token);

        assertThatThrownBy(() -> adapter.verify("bad-token"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode.code", IamErrorCode.AUTH_GOOGLE_TOKEN_INVALID.code());
    }

    @Test
    @DisplayName("verify_throws_when_sub_null_or_blank")
    void verify_throws_when_sub_null_or_blank() throws Exception {
        GoogleIdToken tokenNullSub = mockIdToken(
                null, "user@gmail.com", true,
                "accounts.google.com", null, null);
        Mockito.when(mockDelegate.verify(anyString())).thenReturn(tokenNullSub);

        assertThatThrownBy(() -> adapter.verify("bad-token"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode.code", IamErrorCode.AUTH_GOOGLE_TOKEN_INVALID.code());
    }

    @Test
    @DisplayName("verify_throws_when_email_null_or_blank")
    void verify_throws_when_email_null_or_blank() throws Exception {
        GoogleIdToken tokenNullEmail = mockIdToken(
                "sub-5", null, true,
                "accounts.google.com", null, null);
        Mockito.when(mockDelegate.verify(anyString())).thenReturn(tokenNullEmail);

        assertThatThrownBy(() -> adapter.verify("bad-token"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode.code", IamErrorCode.AUTH_GOOGLE_TOKEN_INVALID.code());
    }

    @Test
    @DisplayName("verify_throws_when_email_not_verified")
    void verify_throws_when_email_not_verified() throws Exception {
        GoogleIdToken tokenUnverified = mockIdToken(
                "sub-6", "user@gmail.com", false,
                "accounts.google.com", null, null);
        Mockito.when(mockDelegate.verify(anyString())).thenReturn(tokenUnverified);

        assertThatThrownBy(() -> adapter.verify("bad-token"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode.code", IamErrorCode.AUTH_GOOGLE_EMAIL_NOT_VERIFIED.code());
    }

    @Test
    @DisplayName("verify_throws_when_client_id_not_configured")
    void verify_throws_when_client_id_not_configured() throws Exception {
        GoogleProperties emptyProps = new GoogleProperties();
        emptyProps.setClientId("");
        GoogleTokenVerifierAdapter emptyAdapter = new GoogleTokenVerifierAdapter(emptyProps);

        assertThatThrownBy(() -> emptyAdapter.verify("any-token"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode.code", IamErrorCode.AUTH_GOOGLE_TOKEN_INVALID.code());
    }

    @Test
    @DisplayName("verify_returns_google_user_info_with_default_name_and_picture_when_null")
    void verify_returns_google_user_info_with_default_name_and_picture_when_null() throws Exception {
        GoogleIdToken token = mockIdToken(
                "sub-7", "user@gmail.com", true,
                "accounts.google.com", null, null);
        Mockito.when(mockDelegate.verify(anyString())).thenReturn(token);

        GoogleUserInfo info = adapter.verify("dummy");

        assertThat(info.name()).isNull();
        assertThat(info.picture()).isNull();
        assertThat(info.verified()).isTrue();
    }

    @Test
    @DisplayName("GoogleTokenVerifierPort_contract_implemented_by_adapter")
    void googleTokenVerifierPort_contract_implemented_by_adapter() {
        assertThat(adapter).isInstanceOf(GoogleTokenVerifierPort.class);
    }
}