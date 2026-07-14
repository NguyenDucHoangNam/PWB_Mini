package com.pwb.backend.modules.iam.controller;

import com.pwb.backend.common.dto.ApiResponse;
import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.common.security.HttpClientContextResolver;
import com.pwb.backend.common.security.captcha.CaptchaContext;
import com.pwb.backend.common.security.captcha.CaptchaVerifier;
import com.pwb.backend.common.security.cookie.RefreshTokenCookieWriter;
import com.pwb.backend.common.security.jwt.BearerTokenExtractor;
import com.pwb.backend.modules.iam.dto.request.GoogleLoginRequest;
import com.pwb.backend.modules.iam.dto.request.LoginRequest;
import com.pwb.backend.modules.iam.dto.request.RegisterRequest;
import com.pwb.backend.modules.iam.dto.request.ResendOtpRequest;
import com.pwb.backend.modules.iam.dto.request.VerifyOtpRequest;
import com.pwb.backend.modules.iam.dto.response.LoginResponse;
import com.pwb.backend.modules.iam.dto.response.RefreshResponse;
import com.pwb.backend.modules.iam.dto.response.RegisterResponse;
import com.pwb.backend.modules.iam.dto.response.ResendOtpResponse;
import com.pwb.backend.modules.iam.exception.IamErrorCode;
import com.pwb.backend.modules.iam.service.AuthService;
import com.pwb.backend.modules.iam.service.GoogleOAuthService;
import com.pwb.backend.modules.iam.service.GoogleUserInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String MSG_REGISTER_INITIATED = "AUTH_REGISTER_INITIATED";
    private static final String MSG_OTP_VERIFIED = "AUTH_OTP_VERIFIED";
    private static final String MSG_OTP_RESENT = "AUTH_OTP_RESENT";
    private static final String MSG_LOGIN_SUCCESSFUL = "AUTH_LOGIN_SUCCESSFUL";
    private static final String MSG_GOOGLE_LOGIN_SUCCESSFUL = "AUTH_GOOGLE_LOGIN_SUCCESSFUL";
    private static final String MSG_TOKEN_REFRESHED = "AUTH_TOKEN_REFRESHED";
    private static final String MSG_LOGOUT_SUCCESSFUL = "AUTH_LOGOUT_SUCCESSFUL";

    private final AuthService authService;
    private final RefreshTokenCookieWriter cookieWriter;
    private final HttpClientContextResolver clientContextResolver;
    private final BearerTokenExtractor bearerTokenExtractor;
    private final CaptchaVerifier captchaVerifier;
    private final MessageSource messageSource;
    private final GoogleOAuthService googleOAuthService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request,
                                                                 HttpServletRequest httpRequest) {
        captchaVerifier.verifyOrThrow(request.captchaToken(), httpRequest, CaptchaContext.register(request.email()));
        String ip = clientContextResolver.resolveIp(httpRequest);
        RegisterResponse response = authService.register(request, ip);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(message(MSG_REGISTER_INITIATED), response));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<LoginResponse>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request,
                                                               HttpServletRequest httpRequest,
                                                               HttpServletResponse httpResponse) {
        captchaVerifier.verifyOrThrow(request.captchaToken(), httpRequest, CaptchaContext.verifyOtp(request.email()));
        String ip = clientContextResolver.resolveIp(httpRequest);
        String userAgent = clientContextResolver.resolveUserAgent(httpRequest);
        LoginResponse data = authService.verifyOtp(request, ip, userAgent);
        captchaVerifier.clearFailure(CaptchaContext.verifyOtp(request.email()));
        writeRefreshCookieIfPresent(httpResponse, data.refreshToken(), data.refreshTokenMaxAgeSeconds());
        return ResponseEntity.ok(ApiResponse.success(message(MSG_OTP_VERIFIED), data));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse<ResendOtpResponse>> resendOtp(@Valid @RequestBody ResendOtpRequest request,
                                                                    HttpServletRequest httpRequest) {
        captchaVerifier.verifyOrThrow(request.captchaToken(), httpRequest, CaptchaContext.resendOtp(request.email()));
        ResendOtpResponse response = authService.resendOtp(request);
        return ResponseEntity.ok(ApiResponse.success(message(MSG_OTP_RESENT), response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
                                                            HttpServletRequest httpRequest,
                                                            HttpServletResponse httpResponse) {
        captchaVerifier.verifyOrThrow(request.captchaToken(), httpRequest, CaptchaContext.login(request.usernameOrEmail()));
        String ip = clientContextResolver.resolveIp(httpRequest);
        String userAgent = clientContextResolver.resolveUserAgent(httpRequest);
        LoginResponse data = authService.login(request, ip, userAgent);
        captchaVerifier.clearFailure(CaptchaContext.login(request.usernameOrEmail()));
        writeRefreshCookieIfPresent(httpResponse, data.refreshToken(), data.refreshTokenMaxAgeSeconds());
        return ResponseEntity.ok(ApiResponse.success(message(MSG_LOGIN_SUCCESSFUL), data));
    }

    @PostMapping("/login/google")
    public ResponseEntity<ApiResponse<LoginResponse>> loginGoogle(@Valid @RequestBody GoogleLoginRequest request,
                                                                  HttpServletRequest httpRequest,
                                                                  HttpServletResponse httpResponse) {
        GoogleUserInfo info = googleOAuthService.verify(request.idToken(), request.nonce());
        String email = info.email().toLowerCase(Locale.ROOT);
        CaptchaContext captchaContext = CaptchaContext.googleLogin(email);
        captchaVerifier.verifyOrThrow(request.captchaToken(), httpRequest, captchaContext);
        String ip = clientContextResolver.resolveIp(httpRequest);
        String userAgent = clientContextResolver.resolveUserAgent(httpRequest);
        LoginResponse data;
        try {
            data = authService.loginWithGoogle(request, ip, userAgent);
        } catch (BusinessException ex) {
            captchaVerifier.recordFailure(captchaContext);
            throw ex;
        }
        captchaVerifier.clearFailure(captchaContext);
        writeRefreshCookieIfPresent(httpResponse, data.refreshToken(), data.refreshTokenMaxAgeSeconds());
        return ResponseEntity.ok(ApiResponse.success(message(MSG_GOOGLE_LOGIN_SUCCESSFUL), data));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(@RequestHeader(name = "Authorization", required = false) String authHeader,
                                                                HttpServletRequest httpRequest,
                                                                HttpServletResponse httpResponse) {
        String expiredAccessToken = bearerTokenExtractor.extract(authHeader);
        String oldRefreshToken = cookieWriter.readRefreshCookie(httpRequest);
        if (oldRefreshToken == null) {
            throw new BusinessException(IamErrorCode.INVALID_REFRESH_TOKEN);
        }
        String ip = clientContextResolver.resolveIp(httpRequest);
        String userAgent = clientContextResolver.resolveUserAgent(httpRequest);
        RefreshResponse data = authService.refresh(expiredAccessToken, oldRefreshToken, ip, userAgent);
        writeRefreshCookieIfPresent(httpResponse, data.refreshToken(), data.refreshTokenMaxAgeSeconds());
        return ResponseEntity.ok(ApiResponse.success(message(MSG_TOKEN_REFRESHED), data));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestHeader(name = "Authorization", required = false) String authHeader,
                                                    HttpServletRequest httpRequest,
                                                    HttpServletResponse httpResponse) {
        String accessToken = bearerTokenExtractor.extract(authHeader);
        String refreshToken = cookieWriter.readRefreshCookie(httpRequest);
        String ip = clientContextResolver.resolveIp(httpRequest);
        authService.logout(accessToken, refreshToken, ip);
        cookieWriter.clearRefreshCookie(httpResponse);
        return ResponseEntity.ok(ApiResponse.success(message(MSG_LOGOUT_SUCCESSFUL)));
    }

    private void writeRefreshCookieIfPresent(HttpServletResponse response, String refreshToken, long maxAgeSeconds) {
        if (maxAgeSeconds <= 0) {
            cookieWriter.clearRefreshCookie(response);
            return;
        }
        if (refreshToken != null && !refreshToken.isBlank()) {
            cookieWriter.writeRefreshCookie(response, refreshToken, maxAgeSeconds);
        }
    }

    private String message(String key, Object... args) {
        return messageSource.getMessage(key, args, key, LocaleContextHolder.getLocale());
    }
}
