package com.pwb.iam.api.controller;

import com.pwb.iam.api.dto.request.ChangePasswordRequest;
import com.pwb.iam.api.dto.request.ForgotPasswordRequest;
import com.pwb.iam.api.dto.request.GoogleLoginRequest;
import com.pwb.iam.api.dto.request.LoginRequest;
import com.pwb.iam.api.dto.request.LogoutRequest;
import com.pwb.iam.api.dto.request.RegisterRequest;
import com.pwb.iam.api.dto.request.ResendOtpRequest;
import com.pwb.iam.api.dto.request.ResetPasswordRequest;
import com.pwb.iam.api.dto.request.VerifyOtpRequest;
import com.pwb.iam.api.dto.response.AuthMessageResponse;
import com.pwb.iam.api.dto.response.AuthResponse;
import com.pwb.iam.api.dto.response.LogoutResponse;
import com.pwb.iam.api.support.RequestLocale;
import com.pwb.iam.application.command.ChangePasswordCommand;
import com.pwb.iam.application.command.ForgotPasswordCommand;
import com.pwb.iam.application.command.GoogleLoginCommand;
import com.pwb.iam.application.command.LoginCommand;
import com.pwb.iam.application.command.LogoutCommand;
import com.pwb.iam.application.command.RefreshTokenCommand;
import com.pwb.iam.application.command.RegisterCommand;
import com.pwb.iam.application.command.ResendOtpCommand;
import com.pwb.iam.application.command.ResetPasswordCommand;
import com.pwb.iam.application.command.VerifyOtpCommand;
import com.pwb.iam.application.facade.AuthView;
import com.pwb.iam.application.facade.IamFacade;
import com.pwb.iam.domain.exception.RefreshTokenInvalidException;
import com.pwb.iam.infrastructure.config.RefreshTokenProperties;
import com.pwb.shared.dto.ApiResponse;
import com.pwb.web.http.CookieUtils;
import com.pwb.web.message.MessageResolver;
import com.pwb.web.security.CurrentClientIp;
import com.pwb.web.security.CurrentUser;
import com.pwb.web.security.CurrentUserAgent;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Endpoints under {@code /api/v1/auth}.
 * <p>
 * The authenticated routes here ({@code /logout}, {@code /change-password}) are not listed in
 * {@code pwb.iam.security.public-endpoints}, so Spring Security rejects anonymous callers before
 * the handler runs. {@code @CurrentUser} is therefore never null and no in-method null check is
 * needed — one that existed would be unreachable.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String MSG_REGISTER = "AUTH_REGISTER_MESSAGE";
    private static final String MSG_OTP_RESENT = "AUTH_OTP_RESENT";
    private static final String MSG_LOGOUT_SUCCESS = "AUTH_LOGOUT_SUCCESSFUL";
    private static final String MSG_FORGOT_PASSWORD = "AUTH_FORGOT_PASSWORD_EMAIL_SENT";
    private static final String MSG_RESET_PASSWORD = "AUTH_PASSWORD_RESET_SUCCESSFUL";
    private static final String MSG_CHANGE_PASSWORD = "AUTH_PASSWORD_UPDATED";

    private final IamFacade iamFacade;
    private final MessageResolver messageResolver;
    private final RefreshTokenProperties refreshTokenProperties;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthMessageResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage
    ) {
        RegisterCommand command = new RegisterCommand(
                request.email(), request.password(), request.fullName(), RequestLocale.from(acceptLanguage));
        UUID userId = iamFacade.register(command);
        AuthMessageResponse body = AuthMessageResponse.of(userId, messageResolver.get(MSG_REGISTER));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(body));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request,
            @CurrentClientIp String clientIp,
            HttpServletResponse response
    ) {
        AuthView view = iamFacade.verifyOtp(
                new VerifyOtpCommand(request.userId(), request.code(), request.purpose(), clientIp));
        return respondWithTokens(view, response);
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse<AuthMessageResponse>> resendOtp(
            @Valid @RequestBody ResendOtpRequest request,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage
    ) {
        iamFacade.resendOtp(new ResendOtpCommand(
                request.userId(), request.purpose(), RequestLocale.from(acceptLanguage)));
        AuthMessageResponse body = AuthMessageResponse.of(request.userId(), messageResolver.get(MSG_OTP_RESENT));
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            @CurrentClientIp String clientIp,
            @CurrentUserAgent String userAgent,
            HttpServletResponse response
    ) {
        AuthView view = iamFacade.login(
                new LoginCommand(request.email(), request.password(), clientIp, userAgent));
        return respondWithTokens(view, response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @CookieValue(name = "${pwb.iam.refresh-token.cookie.name:pwb_refresh_token}", required = false)
            String refreshTokenCookie,
            @CurrentClientIp String clientIp,
            HttpServletResponse response
    ) {
        if (refreshTokenCookie == null || refreshTokenCookie.isBlank()) {
            // Same shape as an invalid token so the caller has a single 401 path to handle.
            throw new RefreshTokenInvalidException("Refresh token cookie is missing");
        }
        AuthView view = iamFacade.refresh(new RefreshTokenCommand(refreshTokenCookie, clientIp));
        return respondWithTokens(view, response);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<LogoutResponse>> logout(
            @CurrentUser UUID userId,
            @CurrentClientIp String clientIp,
            @CurrentUserAgent String userAgent,
            @Valid @RequestBody(required = false) LogoutRequest request,
            @CookieValue(name = "${pwb.iam.refresh-token.cookie.name:pwb_refresh_token}", required = false)
            String refreshTokenCookie,
            HttpServletResponse response
    ) {
        LogoutRequest body = request == null ? LogoutRequest.empty() : request;
        String refreshTokenToRevoke = (refreshTokenCookie != null && !refreshTokenCookie.isBlank())
                ? refreshTokenCookie
                : body.refreshToken();

        LogoutCommand command = new LogoutCommand(
                userId,
                refreshTokenToRevoke,
                body.accessJti(),
                // Nullable in the request: a client that only relies on the cookie sends neither
                // the jti nor its lifetime, and unboxing null into the primitive would 500.
                body.accessExpiresInSeconds() == null ? 0L : body.accessExpiresInSeconds(),
                clientIp,
                userAgent
        );
        UUID resultUserId = iamFacade.logout(command);

        clearRefreshTokenCookie(response);
        LogoutResponse logoutResponse = LogoutResponse.of(resultUserId, messageResolver.get(MSG_LOGOUT_SUCCESS));
        return ResponseEntity.ok(ApiResponse.success(logoutResponse));
    }

    @PostMapping("/google-login")
    public ResponseEntity<ApiResponse<AuthResponse>> googleLogin(
            @Valid @RequestBody GoogleLoginRequest request,
            @CurrentClientIp String clientIp,
            @CurrentUserAgent String userAgent,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage,
            HttpServletResponse response
    ) {
        AuthView view = iamFacade.loginWithGoogle(new GoogleLoginCommand(
                request.idToken(), clientIp, userAgent, RequestLocale.from(acceptLanguage)));
        return respondWithTokens(view, response);
    }

    /**
     * Always answers with the same body, whether or not the address is registered — see
     * {@code ForgotPasswordUseCaseImpl}. In particular no userId is echoed back.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<AuthMessageResponse>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            @CurrentUserAgent String userAgent,
            @CurrentClientIp String clientIp,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage
    ) {
        iamFacade.forgotPassword(new ForgotPasswordCommand(
                request.email(), userAgent, RequestLocale.from(acceptLanguage), clientIp));
        AuthMessageResponse body = AuthMessageResponse.of(null, messageResolver.get(MSG_FORGOT_PASSWORD));
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<AuthMessageResponse>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            @CurrentUserAgent String userAgent,
            @CurrentClientIp String clientIp,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage
    ) {
        UUID userId = iamFacade.resetPassword(new ResetPasswordCommand(
                request.token(), request.newPassword(), userAgent, clientIp,
                RequestLocale.from(acceptLanguage)));
        AuthMessageResponse body = AuthMessageResponse.of(userId, messageResolver.get(MSG_RESET_PASSWORD));
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<AuthMessageResponse>> changePassword(
            @CurrentUser UUID userId,
            @CurrentUserAgent String userAgent,
            @CurrentClientIp String clientIp,
            @Valid @RequestBody ChangePasswordRequest request,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage
    ) {
        UUID resultUserId = iamFacade.changePassword(new ChangePasswordCommand(
                userId, request.currentPassword(), request.newPassword(), userAgent, clientIp,
                RequestLocale.from(acceptLanguage)));
        AuthMessageResponse body = AuthMessageResponse.of(resultUserId, messageResolver.get(MSG_CHANGE_PASSWORD));
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    private ResponseEntity<ApiResponse<AuthResponse>> respondWithTokens(AuthView view, HttpServletResponse response) {
        setRefreshTokenCookie(response, view.refreshToken());
        return ResponseEntity.ok(ApiResponse.success(AuthResponse.from(view)));
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        if (refreshToken == null) {
            return;
        }
        RefreshTokenProperties.CookieConfig cookie = refreshTokenProperties.getCookie();
        CookieUtils.addRefreshTokenCookie(
                response,
                cookie.getName(),
                refreshToken,
                cookie.getPath(),
                cookie.isHttpOnly(),
                cookie.isSecure(),
                cookie.getSameSite(),
                cookie.getMaxAgeSeconds()
        );
    }

    /**
     * Cleared with the same name/path/attributes it was set with — a browser only replaces a
     * cookie when those match, so a hard-coded path would silently leave the session cookie
     * in place whenever the configured path is not "/".
     */
    private void clearRefreshTokenCookie(HttpServletResponse response) {
        RefreshTokenProperties.CookieConfig cookie = refreshTokenProperties.getCookie();
        CookieUtils.clearRefreshTokenCookie(
                response,
                cookie.getName(),
                cookie.getPath(),
                cookie.isHttpOnly(),
                cookie.isSecure(),
                cookie.getSameSite()
        );
    }
}
