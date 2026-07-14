package com.pwb.backend.modules.iam.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxmind.geoip2.DatabaseReader;
import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.common.outbox.model.OutboxEvent;
import com.pwb.backend.common.security.jwt.JwtProperties;
import com.pwb.backend.common.security.jwt.JwtSigner;
import com.pwb.backend.common.outbox.publisher.OutboxPayloadCipher;
import com.pwb.backend.common.outbox.repository.OutboxEventRepository;
import com.pwb.backend.common.util.PasswordHasher;
import com.pwb.backend.common.util.SecureRandomOtpGenerator;
import com.pwb.backend.modules.iam.dto.request.GoogleLoginRequest;
import com.pwb.backend.modules.iam.dto.request.LoginRequest;
import com.pwb.backend.modules.iam.dto.request.RegisterRequest;
import com.pwb.backend.modules.iam.dto.request.ResendOtpRequest;
import com.pwb.backend.modules.iam.dto.request.VerifyOtpRequest;
import com.pwb.backend.modules.iam.dto.response.LoginResponse;
import com.pwb.backend.modules.iam.dto.response.RefreshResponse;
import com.pwb.backend.modules.iam.dto.response.RegisterResponse;
import com.pwb.backend.modules.iam.dto.response.ResendOtpResponse;
import com.pwb.backend.modules.iam.dto.response.VerifyOtpResponse;
import com.pwb.backend.modules.iam.enums.OauthProvider;
import com.pwb.backend.modules.iam.enums.RoleType;
import com.pwb.backend.modules.iam.enums.UserStatus;
import com.pwb.backend.modules.iam.exception.IamErrorCode;
import com.pwb.backend.modules.iam.mapper.UserMapper;
import com.pwb.backend.modules.iam.model.Role;
import com.pwb.backend.modules.iam.model.User;
import com.pwb.backend.modules.iam.repository.RoleRepository;
import com.pwb.backend.modules.iam.repository.UserRepository;
import com.pwb.backend.modules.iam.service.GoogleOAuthService;
import com.pwb.backend.modules.iam.service.GoogleUserInfo;
import com.pwb.backend.modules.iam.service.LoginAttemptService;
import com.pwb.backend.modules.iam.service.OtpService;
import com.pwb.backend.modules.iam.service.SessionService;
import com.pwb.backend.modules.iam.session.IssuedSession;
import com.pwb.backend.modules.iam.session.RotationResult;
import com.pwb.backend.modules.iam.session.RotationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private OutboxEventRepository outboxRepository;

    @Mock
    private OtpService otpService;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private SecureRandomOtpGenerator otpGenerator;

    @Mock
    private UserMapper userMapper;

    @Mock
    private JwtSigner jwtSigner;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private SessionService sessionService;

    @Mock
    private GoogleOAuthService googleOAuthService;

    @Mock
    private OutboxPayloadCipher outboxCipher;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private DatabaseReader geoIpDatabaseReader;

    private AuthServiceImpl authService;

    private final String email = "test@example.com";
    private final String password = "Password@123";
    private Role userRole;
    private User pendingUser;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                userRepository,
                roleRepository,
                outboxRepository,
                otpService,
                passwordHasher,
                otpGenerator,
                userMapper,
                jwtSigner,
                jwtProperties,
                eventPublisher,
                objectMapper,
                loginAttemptService,
                sessionService,
                googleOAuthService,
                outboxCipher,
                stringRedisTemplate,
                geoIpDatabaseReader
        );

        userRole = new Role(UUID.randomUUID(), RoleType.USER.code(), "User");

        pendingUser = User.newPending(email, "hashed_password", "Test Name", userRole);
        ReflectionTestUtils.setField(pendingUser, "id", UUID.randomUUID());
    }

    @Test
    void register_success() throws Exception {
        RegisterRequest request = new RegisterRequest(email, password, "Test Name", "captcha_token");

        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(roleRepository.findByCode(RoleType.USER.code())).thenReturn(Optional.of(userRole));
        when(passwordHasher.hash(password)).thenReturn("hashed_password");
        when(otpGenerator.generate()).thenReturn("123456");
        when(userMapper.userToRegisterResponse(any(User.class)))
                .thenReturn(new RegisterResponse(email, "PENDING_VERIFICATION", Instant.now().plusSeconds(300)));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        RegisterResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals(email, response.email());
        verify(userRepository).save(any(User.class));
        verify(otpService).issueOtp(email, "123456");
        verify(outboxRepository).save(any(OutboxEvent.class));
    }

    @Test
    void register_throwsEmailExistsException() {
        RegisterRequest request = new RegisterRequest(email, password, "Test Name", "captcha_token");
        when(userRepository.existsByEmail(email)).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.register(request));
        assertEquals(IamErrorCode.EMAIL_ALREADY_EXISTS, exception.getErrorCode());
    }

    @Test
    void verifyOtp_success() {
        VerifyOtpRequest request = new VerifyOtpRequest(email, "123456");

        when(userRepository.findByEmailForUpdate(email)).thenReturn(Optional.of(pendingUser));
        when(otpService.isLocked(email)).thenReturn(false);
        when(otpService.verifyOtp(email, "123456")).thenReturn(true);
        when(jwtSigner.generateAccessToken(any(), eq(email), eq("USER"))).thenReturn("access_token");
        when(jwtProperties.getAccessTokenTtlSeconds()).thenReturn(900L);

        VerifyOtpResponse response = authService.verifyOtp(request);

        assertNotNull(response);
        assertEquals("access_token", response.accessToken());
        assertEquals(UserStatus.ACTIVE, pendingUser.getStatus());
        verify(userRepository).save(pendingUser);
    }

    @Test
    void verifyOtp_throwsOtpLocked() {
        VerifyOtpRequest request = new VerifyOtpRequest(email, "123456");
        when(userRepository.findByEmailForUpdate(email)).thenReturn(Optional.of(pendingUser));
        when(otpService.isLocked(email)).thenReturn(true);
        when(otpService.lockoutRetryAfterSeconds()).thenReturn(900L);

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.verifyOtp(request));
        assertEquals(IamErrorCode.OTP_LOCKED, exception.getErrorCode());
    }

    @Test
    void resendOtp_success() throws Exception {
        ResendOtpRequest request = new ResendOtpRequest(email, "captcha_token");
        when(otpService.canResend(email)).thenReturn(true);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(pendingUser));
        when(otpGenerator.generate()).thenReturn("123456");
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        ResendOtpResponse response = authService.resendOtp(request);

        assertNotNull(response);
        assertEquals(email, response.email());
        verify(otpService).issueOtp(email, "123456");
        verify(otpService).markResent(email);
        verify(outboxRepository).save(any(OutboxEvent.class));
    }

    @Test
    void resendOtp_throwsCooldownException() {
        ResendOtpRequest request = new ResendOtpRequest(email, "captcha_token");
        when(otpService.canResend(email)).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.resendOtp(request));
        assertEquals(IamErrorCode.OTP_RESEND_COOLDOWN, exception.getErrorCode());
    }

    @Test
    void login_success() {
        LoginRequest request = new LoginRequest(email, password, "captcha_token");
        User activeUser = User.newPending(email, "hashed_password", "Test Name", userRole);
        activeUser.setStatus(UserStatus.ACTIVE);
        ReflectionTestUtils.setField(activeUser, "id", UUID.randomUUID());

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(activeUser));
        when(passwordHasher.matches(password, "hashed_password")).thenReturn(true);
        when(jwtProperties.getRefreshTokenTtlSeconds()).thenReturn(604800L);
        when(jwtProperties.getAccessTokenTtlSeconds()).thenReturn(900L);
        when(sessionService.grantInitialSession(activeUser.getId()))
                .thenReturn(new IssuedSession(activeUser.getId(), "refresh_token", Instant.now().plusSeconds(604800L), null));
        when(jwtSigner.generateAccessToken(activeUser.getId(), email, "USER")).thenReturn("access_token");

        LoginResponse response = authService.login(request, "127.0.0.1", "Mozilla");

        assertNotNull(response);
        assertEquals("access_token", response.accessToken());
        assertEquals("refresh_token", response.refreshToken());
        verify(loginAttemptService).clearFailures(activeUser.getId());
        verify(loginAttemptService).clearIpFailures("127.0.0.1");
    }

    @Test
    void login_throwsBadCredentials() {
        LoginRequest request = new LoginRequest(email, password, "captcha_token");
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.login(request, "127.0.0.1", "Mozilla"));
        assertEquals(IamErrorCode.BAD_CREDENTIALS, exception.getErrorCode());
    }

    @Test
    void loginWithGoogle_success() {
        GoogleLoginRequest request = new GoogleLoginRequest("google_id_token", "nonce");
        GoogleUserInfo googleUserInfo = new GoogleUserInfo(email, "Google User", "avatar_url", "google-id");

        when(googleOAuthService.verify("google_id_token", "nonce")).thenReturn(googleUserInfo);
        when(userRepository.findByOauthProviderAndOauthId(OauthProvider.GOOGLE, "google-id")).thenReturn(Optional.empty());
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(roleRepository.findByCode(RoleType.USER.code())).thenReturn(Optional.of(userRole));

        User googleUser = User.newOAuthActive(email, "Google User", "google-id", "avatar_url", userRole);
        ReflectionTestUtils.setField(googleUser, "id", UUID.randomUUID());
        when(userRepository.save(any(User.class))).thenReturn(googleUser);

        when(jwtProperties.getRefreshTokenTtlSeconds()).thenReturn(604800L);
        when(jwtProperties.getAccessTokenTtlSeconds()).thenReturn(900L);
        when(sessionService.grantInitialSession(any()))
                .thenReturn(new IssuedSession(UUID.randomUUID(), "refresh_token", Instant.now().plusSeconds(604800L), null));
        when(jwtSigner.generateAccessToken(any(), eq(email), eq("USER"))).thenReturn("access_token");

        LoginResponse response = authService.loginWithGoogle(request, "127.0.0.1", "Mozilla");

        assertNotNull(response);
        assertEquals("access_token", response.accessToken());
    }

    @Test
    void refresh_success() {
        String expiredAccessToken = "expired_access_token";
        String oldRefreshToken = "old_refresh_token";
        UUID userId = UUID.randomUUID();

        when(jwtSigner.parseExpiredTokenUserId(expiredAccessToken)).thenReturn(userId);
        User activeUser = User.newPending(email, "hashed_password", "Test Name", userRole);
        activeUser.setStatus(UserStatus.ACTIVE);
        ReflectionTestUtils.setField(activeUser, "id", userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));

        when(sessionService.rotate(oldRefreshToken, userId))
                .thenReturn(new RotationResult(RotationStatus.ROTATED, userId, "new_refresh_token", true));
        when(jwtSigner.generateAccessToken(userId, email, "USER")).thenReturn("new_access_token");
        when(jwtProperties.getAccessTokenTtlSeconds()).thenReturn(900L);
        when(jwtProperties.getRefreshTokenTtlSeconds()).thenReturn(604800L);

        RefreshResponse response = authService.refresh(expiredAccessToken, oldRefreshToken, "127.0.0.1", "Mozilla");

        assertNotNull(response);
        assertEquals("new_access_token", response.accessToken());
        assertEquals("new_refresh_token", response.refreshToken());
    }

    @Test
    void verifyOtp_userNotFound() {
        VerifyOtpRequest request = new VerifyOtpRequest(email, "123456");
        when(userRepository.findByEmailForUpdate(email)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.verifyOtp(request));
        assertEquals(IamErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void verifyOtp_invalidOtp() {
        VerifyOtpRequest request = new VerifyOtpRequest(email, "wrong123");
        when(userRepository.findByEmailForUpdate(email)).thenReturn(Optional.of(pendingUser));
        when(otpService.isLocked(email)).thenReturn(false);
        when(otpService.verifyOtp(email, "wrong123")).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.verifyOtp(request));
        assertEquals(IamErrorCode.INVALID_OTP, exception.getErrorCode());
    }

    @Test
    void verifyOtp_userAlreadyActive() {
        pendingUser.setStatus(UserStatus.ACTIVE);
        VerifyOtpRequest request = new VerifyOtpRequest(email, "123456");
        when(userRepository.findByEmailForUpdate(email)).thenReturn(Optional.of(pendingUser));

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.verifyOtp(request));
        assertEquals(IamErrorCode.USER_ALREADY_VERIFIED, exception.getErrorCode());
    }

    @Test
    void login_wrongPassword() {
        LoginRequest request = new LoginRequest(email, "WrongPassword@123", "captcha_token");
        User activeUser = User.newPending(email, "hashed_password", "Test Name", userRole);
        activeUser.setStatus(UserStatus.ACTIVE);
        ReflectionTestUtils.setField(activeUser, "id", UUID.randomUUID());

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(activeUser));
        when(passwordHasher.matches("WrongPassword@123", "hashed_password")).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.login(request, "127.0.0.1", "Mozilla"));
        assertEquals(IamErrorCode.BAD_CREDENTIALS, exception.getErrorCode());
        verify(loginAttemptService).recordFailure(activeUser.getId());
    }

    @Test
    void login_accountLocked() {
        LoginRequest request = new LoginRequest(email, password, "captcha_token");
        User activeUser = User.newPending(email, "hashed_password", "Test Name", userRole);
        activeUser.setStatus(UserStatus.ACTIVE);
        ReflectionTestUtils.setField(activeUser, "id", UUID.randomUUID());

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(activeUser));
        doThrow(new BusinessException(IamErrorCode.ACCOUNT_TEMPORARILY_LOCKED))
                .when(loginAttemptService).validateNotLocked(activeUser.getId());

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.login(request, "127.0.0.1", "Mozilla"));
        assertEquals(IamErrorCode.ACCOUNT_TEMPORARILY_LOCKED, exception.getErrorCode());
    }

    @Test
    void login_accountBanned() {
        LoginRequest request = new LoginRequest(email, password, "captcha_token");
        User bannedUser = User.newPending(email, "hashed_password", "Test Name", userRole);
        bannedUser.setStatus(UserStatus.BANNED);
        ReflectionTestUtils.setField(bannedUser, "id", UUID.randomUUID());

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(bannedUser));

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.login(request, "127.0.0.1", "Mozilla"));
        assertEquals(IamErrorCode.BAD_CREDENTIALS, exception.getErrorCode());
    }

    @Test
    void loginWithGoogle_existingOAuthUser() {
        GoogleLoginRequest request = new GoogleLoginRequest("google_id_token", "nonce");
        GoogleUserInfo googleUserInfo = new GoogleUserInfo(email, "Updated Name", "new_avatar_url", "google-id");

        User existingOAuthUser = User.newOAuthActive(email, "Old Name", "google-id", "old_avatar_url", userRole);
        ReflectionTestUtils.setField(existingOAuthUser, "id", UUID.randomUUID());

        when(googleOAuthService.verify("google_id_token", "nonce")).thenReturn(googleUserInfo);
        when(userRepository.findByOauthProviderAndOauthId(OauthProvider.GOOGLE, "google-id")).thenReturn(Optional.of(existingOAuthUser));
        when(jwtProperties.getRefreshTokenTtlSeconds()).thenReturn(604800L);
        when(jwtProperties.getAccessTokenTtlSeconds()).thenReturn(900L);
        when(sessionService.grantInitialSession(any()))
                .thenReturn(new IssuedSession(UUID.randomUUID(), "refresh_token", Instant.now().plusSeconds(604800L), null));
        when(jwtSigner.generateAccessToken(any(), eq(email), eq("USER"))).thenReturn("access_token");

        LoginResponse response = authService.loginWithGoogle(request, "127.0.0.1", "Mozilla");

        assertNotNull(response);
        verify(userRepository, atLeastOnce()).save(existingOAuthUser);
    }

    @Test
    void loginWithGoogle_userBanned() {
        GoogleLoginRequest request = new GoogleLoginRequest("google_id_token", "nonce");
        GoogleUserInfo googleUserInfo = new GoogleUserInfo(email, "Google User", "avatar_url", "google-id");

        User bannedUser = User.newOAuthActive(email, "Google User", "google-id", "avatar_url", userRole);
        bannedUser.setStatus(UserStatus.BANNED);
        ReflectionTestUtils.setField(bannedUser, "id", UUID.randomUUID());

        when(googleOAuthService.verify("google_id_token", "nonce")).thenReturn(googleUserInfo);
        when(userRepository.findByOauthProviderAndOauthId(OauthProvider.GOOGLE, "google-id")).thenReturn(Optional.of(bannedUser));

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.loginWithGoogle(request, "127.0.0.1", "Mozilla"));
        assertEquals(IamErrorCode.ACCOUNT_BANNED, exception.getErrorCode());
    }

    @Test
    void logout_success() {
        String accessToken = "access_token";
        String refreshToken = "refresh_token";

        when(jwtSigner.extractSignature(accessToken)).thenReturn("signature");
        when(jwtSigner.extractExpiryEpochSecond(accessToken)).thenReturn(Instant.now().getEpochSecond() + 900L);
        when(jwtProperties.getBlacklistClockSkewBufferSeconds()).thenReturn(30L);

        authService.logout(accessToken, refreshToken, "127.0.0.1");

        verify(sessionService).blacklistAccessToken(eq("signature"), anyLong());
        verify(sessionService).revokeSingleSession(refreshToken);
    }

    @Test
    void logout_withNullTokens_noOperations() {
        authService.logout(null, null, "127.0.0.1");

        verify(sessionService, never()).blacklistAccessToken(anyString(), anyLong());
        verify(sessionService, never()).revokeSingleSession(anyString());
    }
}
