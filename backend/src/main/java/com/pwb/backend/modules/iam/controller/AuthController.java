package com.pwb.backend.modules.iam.controller;

import com.pwb.backend.common.dto.ApiResponse;
import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.common.security.HttpClientContextResolver;
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
import com.pwb.backend.modules.iam.dto.response.VerifyOtpResponse;
import com.pwb.backend.modules.iam.exception.IamErrorCode;
import com.pwb.backend.modules.iam.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
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

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request,
                                                                 HttpServletRequest httpRequest) {
        captchaVerifier.verifyOrThrow(request.captchaToken(), httpRequest);
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(message(MSG_REGISTER_INITIATED), response));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<VerifyOtpResponse>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        VerifyOtpResponse response = authService.verifyOtp(request);
        return ResponseEntity.ok(ApiResponse.success(message(MSG_OTP_VERIFIED), response));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse<ResendOtpResponse>> resendOtp(@Valid @RequestBody ResendOtpRequest request,
                                                                    HttpServletRequest httpRequest) {
        captchaVerifier.verifyOrThrow(request.captchaToken(), httpRequest);
        ResendOtpResponse response = authService.resendOtp(request);
        return ResponseEntity.ok(ApiResponse.success(message(MSG_OTP_RESENT), response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
                                                            HttpServletRequest httpRequest,
                                                            HttpServletResponse httpResponse) {
        captchaVerifier.verifyOrThrow(request.captchaToken(), httpRequest);
        String ip = clientContextResolver.resolveIp(httpRequest);
        String userAgent = clientContextResolver.resolveUserAgent(httpRequest);
        LoginResponse data = authService.login(request, ip, userAgent);
        writeRefreshCookieIfPresent(httpResponse, data.refreshToken(), data.refreshTokenMaxAgeSeconds());
        return ResponseEntity.ok(ApiResponse.success(message(MSG_LOGIN_SUCCESSFUL), data));
    }

    @PostMapping("/login/google")
    public ResponseEntity<ApiResponse<LoginResponse>> loginGoogle(@Valid @RequestBody GoogleLoginRequest request,
                                                                  HttpServletRequest httpRequest,
                                                                  HttpServletResponse httpResponse) {
        String ip = clientContextResolver.resolveIp(httpRequest);
        String userAgent = clientContextResolver.resolveUserAgent(httpRequest);
        LoginResponse data = authService.loginWithGoogle(request, ip, userAgent);
        writeRefreshCookieIfPresent(httpResponse, data.refreshToken(), data.refreshTokenMaxAgeSeconds());
        return ResponseEntity.ok(ApiResponse.success(message(MSG_GOOGLE_LOGIN_SUCCESSFUL), data));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(@RequestHeader(name = "Authorization", required = false) String authHeader,
                                                                HttpServletRequest httpRequest,
                                                                HttpServletResponse httpResponse) {
        String expiredAccessToken = bearerTokenExtractor.extractOrThrow(authHeader);
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
        try {
            String accessToken = bearerTokenExtractor.extract(authHeader);
            String refreshToken = cookieWriter.readRefreshCookie(httpRequest);
            String ip = clientContextResolver.resolveIp(httpRequest);
            authService.logout(accessToken, refreshToken, ip);
        } finally {
            cookieWriter.clearRefreshCookie(httpResponse);
        }
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
