package com.pwb.backend.service.impl;

import com.pwb.backend.utils.helper.MessageHelper;
import com.pwb.backend.dto.request.CompleteProfileRequest;
import com.pwb.backend.dto.request.LoginRequest;
import com.pwb.backend.dto.request.RegisterRequest;
import com.pwb.backend.dto.request.ResendOtpRequest;
import com.pwb.backend.dto.request.VerifyOtpRequest;
import com.pwb.backend.dto.response.AuthMessageResponse;
import com.pwb.backend.dto.response.AuthResponse;
import com.pwb.backend.entity.rdbms.Role;
import com.pwb.backend.entity.rdbms.User;
import com.pwb.backend.enums.OAuthProvider;
import com.pwb.backend.enums.RoleName;
import com.pwb.backend.enums.UserStatus;
import com.pwb.backend.exception.BusinessException;
import com.pwb.backend.exception.ErrorCode;
import com.pwb.backend.mapper.AuthMapper;
import com.pwb.backend.repository.rdbms.UserRepository;
import com.pwb.backend.security.CustomUserDetails;
import com.pwb.backend.security.jwt.JwtTokenProvider;
import com.pwb.backend.service.AuthEventPublisher;
import com.pwb.backend.service.AuthService;
import com.pwb.backend.service.OtpService;
import com.pwb.backend.service.RoleLookupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String MSG_REGISTER_EMAIL = "auth.register.email_sent";
    private static final String MSG_RESEND = "auth.resend.success";
    private static final String MSG_LOGOUT = "auth.logout.success";
    private static final String OTP_PURPOSE = OtpService.PURPOSE_REGISTER;

    private final UserRepository userRepository;
    private final RoleLookupService roleLookupService;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthEventPublisher authEventPublisher;
    private final AuthMapper authMapper;
    private final MessageHelper messageHelper;

    @Override
    @Transactional
    public AuthMessageResponse register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmailAndDeletedFalse(email)) {
            throw new BusinessException(ErrorCode.USER_EMAIL_EXISTS);
        }

        Role defaultRole = lookupRole(RoleName.USER);
        String provisionalUsername = generateProvisionalUsername();

        User user = User.builder()
                .email(email)
                .username(provisionalUsername)
                .password(passwordEncoder.encode(request.getPassword()))
                .status(UserStatus.PENDING_VERIFICATION)
                .oauthProvider(OAuthProvider.LOCAL)
                .role(defaultRole)
                .build();
        user = userRepository.save(user);

        scheduleOtpDeliveryAfterCommit(user.getId(), user.getEmail(), OTP_PURPOSE);

        String message = messageHelper.get(MSG_REGISTER_EMAIL, user.getEmail());
        log.info("User registered pending verification: userId={} email={}", user.getId(), user.getEmail());
        return AuthMessageResponse.of(user.getId(), message);
    }

    private static final int PROVISIONAL_USERNAME_RANDOM_LENGTH = 16;

    private static String generateProvisionalUsername() {
        String hex = UUID.randomUUID().toString().replace("-", "");
        return "user_" + hex.substring(0, PROVISIONAL_USERNAME_RANDOM_LENGTH);
    }

    @Override
    @Transactional
    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        User user = userRepository.findByIdAndDeletedFalse(request.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getStatus() != UserStatus.PENDING_VERIFICATION) {
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_NOT_VERIFIED);
        }

        OtpService.VerificationResult result = otpService.verify(request.getUserId(), OTP_PURPOSE, request.getCode());
        switch (result) {
            case LOCKED -> throw new BusinessException(ErrorCode.AUTH_OTP_LOCKED);
            case INVALID, EXPIRED_OR_MISSING -> throw new BusinessException(ErrorCode.AUTH_OTP_INVALID);
            default -> { /* OK */ }
        }

        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        log.info("User verified OTP successfully: userId={}", user.getId());
        return buildAuthResponse(user, AuthResponse.NextStep.COMPLETE_PROFILE);
    }

    @Override
    @Transactional
    public AuthResponse completeProfile(UUID userId, CompleteProfileRequest request) {
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String newUsername = request.getUsername().trim();
        if (!user.getUsername().equals(newUsername)
                && userRepository.existsByUsernameAndDeletedFalse(newUsername)) {
            throw new BusinessException(ErrorCode.USER_NAME_EXISTS);
        }

        user.setUsername(newUsername);
        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName().trim());
        }
        if (request.getNewPassword() != null && !request.getNewPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        }
        userRepository.save(user);

        log.info("Profile completed: userId={} username={}", user.getId(), user.getUsername());
        return buildAuthResponse(user, AuthResponse.NextStep.NONE);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_LOGIN_FAILED));

        if (user.getStatus() != UserStatus.ACTIVE) {
            if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
                throw new BusinessException(ErrorCode.AUTH_ACCOUNT_NOT_VERIFIED);
            }
            throw new BusinessException(ErrorCode.AUTH_LOGIN_FAILED);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.AUTH_LOGIN_FAILED);
        }

        authEventPublisher.publishLoginSuccess(user.getId(), user.getEmail());
        log.info("User login success: userId={}", user.getId());
        return buildAuthResponse(user, AuthResponse.NextStep.NONE);
    }

    @Override
    @Transactional
    public AuthResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        if (!jwtTokenProvider.validateRefreshToken(refreshToken)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        UUID userId = jwtTokenProvider.extractUserIdFromRefreshToken(refreshToken);
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_NOT_VERIFIED);
        }

        log.info("Token refreshed: userId={}", user.getId());
        return buildAuthResponse(user, AuthResponse.NextStep.NONE);
    }

    @Override
    @Transactional
    public AuthMessageResponse logout(UUID userId) {
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        authEventPublisher.publishLogout(user.getId(), user.getEmail());
        log.info("User logged out: userId={}", user.getId());
        return AuthMessageResponse.of(user.getId(), messageHelper.get(MSG_LOGOUT));
    }

    @Override
    @Transactional
    public AuthMessageResponse resendOtp(ResendOtpRequest request) {
        User user = userRepository.findByIdAndDeletedFalse(request.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getStatus() != UserStatus.PENDING_VERIFICATION) {
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_NOT_VERIFIED);
        }

        if (!otpService.canResend(user.getId(), OTP_PURPOSE)) {
            throw new BusinessException(ErrorCode.AUTH_OTP_DAILY_LIMIT);
        }

        Duration cooldown = otpService.resendCooldownRemaining(user.getId(), OTP_PURPOSE);
        if (!cooldown.isZero()) {
            throw new BusinessException(ErrorCode.AUTH_OTP_RATE_LIMIT, cooldown.toSeconds());
        }

        otpService.invalidate(user.getId(), OTP_PURPOSE);
        scheduleOtpDeliveryAfterCommit(user.getId(), user.getEmail(), OTP_PURPOSE);

        log.info("OTP resent: userId={}", user.getId());
        return AuthMessageResponse.of(user.getId(), messageHelper.get(MSG_RESEND));
    }

    private void scheduleOtpDeliveryAfterCommit(UUID userId, String email, String purpose) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        String otp = otpService.generateAndStore(userId, purpose);
                        authEventPublisher.publishUserRegisteredOtp(userId, email, otp);
                    } catch (RuntimeException ex) {
                        log.error("Failed to deliver OTP after commit: userId={}", userId, ex);
                    }
                }
            });
        } else {
            String otp = otpService.generateAndStore(userId, purpose);
            authEventPublisher.publishUserRegisteredOtp(userId, email, otp);
        }
    }

    private AuthResponse buildAuthResponse(User user, AuthResponse.NextStep nextStep) {
        AuthResponse response = authMapper.toAuthResponse(user, nextStep);
        response.setAccessToken(jwtTokenProvider.generateAccessToken(new CustomUserDetails(user)));
        response.setRefreshToken(jwtTokenProvider.generateRefreshToken(new CustomUserDetails(user)));
        response.setExpiresIn(jwtTokenProvider.getAccessTokenExpirationSeconds());
        return response;
    }

    private Role lookupRole(RoleName roleName) {
        try {
            return roleLookupService.requireRole(roleName.name());
        } catch (IllegalStateException ex) {
            throw new BusinessException(ErrorCode.SEEDER_ROLE_NOT_FOUND, roleName.name());
        }
    }
}
