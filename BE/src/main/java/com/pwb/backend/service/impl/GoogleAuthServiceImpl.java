package com.pwb.backend.service.impl;

import com.pwb.backend.dto.request.GoogleLoginRequest;
import com.pwb.backend.dto.response.AuthResponse;
import com.pwb.backend.dto.response.AuthResponse.NextStep;
import com.pwb.backend.entity.rdbms.Role;
import com.pwb.backend.entity.rdbms.User;
import com.pwb.backend.enums.OAuthProvider;
import com.pwb.backend.enums.RoleName;
import com.pwb.backend.enums.UserStatus;
import com.pwb.backend.exception.BusinessException;
import com.pwb.backend.exception.ErrorCode;
import com.pwb.backend.dto.GoogleIdTokenPayload;
import com.pwb.backend.security.GoogleTokenVerifier;
import com.pwb.backend.mapper.AuthMapper;
import com.pwb.backend.repository.rdbms.UserRepository;
import com.pwb.backend.security.CustomUserDetails;
import com.pwb.backend.security.jwt.JwtTokenProvider;
import com.pwb.backend.service.AuthEventPublisher;
import com.pwb.backend.service.GoogleAuthService;
import com.pwb.backend.service.RoleLookupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleAuthServiceImpl implements GoogleAuthService {

    private static final String PLACEHOLDER_PREFIX = "OAUTH_PLACEHOLDER_";

    private final UserRepository userRepository;
    private final RoleLookupService roleLookupService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthMapper authMapper;
    private final AuthEventPublisher authEventPublisher;

    @Override
    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        GoogleIdTokenPayload payload = googleTokenVerifier.verify(request.getIdToken());

        User user = userRepository
                .findByOauthProviderAndOauthIdAndDeletedFalse(OAuthProvider.GOOGLE, payload.sub())
                .orElse(null);

        if (user == null) {
            user = userRepository.findByEmailAndDeletedFalse(payload.email()).orElse(null);

            if (user != null) {
                user = linkExistingUser(user, payload);
            } else {
                user = createGoogleUser(payload);
            }
        } else {
            user = activateIfNeeded(user, payload);
        }

        if (user.getStatus() == UserStatus.BANNED || user.getStatus() == UserStatus.DELETED) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
        }

        authEventPublisher.publishLoginSuccess(user.getId(), user.getEmail());
        log.info("Google login success: userId={} email={}", user.getId(), user.getEmail());

        NextStep nextStep = (user.getUsername() != null && user.getUsername().startsWith("user_"))
                || user.getFullName() == null || user.getFullName().isBlank()
                ? NextStep.COMPLETE_PROFILE : NextStep.NONE;

        return buildAuthResponse(user, nextStep);
    }

    private User linkExistingUser(User user, GoogleIdTokenPayload payload) {
        user.setOauthProvider(OAuthProvider.GOOGLE);
        user.setOauthId(payload.sub());
        if (payload.picture() != null && !payload.picture().isBlank()) {
            user.setAvatarUrl(payload.picture());
        }
        if (payload.name() != null && !payload.name().isBlank()
                && (user.getFullName() == null || user.getFullName().isBlank())) {
            user.setFullName(payload.name());
        }
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            user.setStatus(UserStatus.ACTIVE);
        }
        User saved = userRepository.save(user);
        scheduleLinkedGoogleDeliveryAfterCommit(saved);
        log.info("Linking existing account with Google: userId={} email={}", saved.getId(), saved.getEmail());
        return saved;
    }

    private void scheduleLinkedGoogleDeliveryAfterCommit(User user) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        authEventPublisher.publishUserLinkedGoogle(
                                user.getId(), user.getEmail(), user.getFullName());
                    } catch (RuntimeException ex) {
                        log.error("Failed to publish linked Google event after commit: userId={}", user.getId(), ex);
                    }
                }
            });
        } else {
            log.warn("No active transaction for linked Google userId={} - publishing inline", user.getId());
            authEventPublisher.publishUserLinkedGoogle(user.getId(), user.getEmail(), user.getFullName());
        }
    }

    private User createGoogleUser(GoogleIdTokenPayload payload) {
        Role defaultRole = lookupRole(RoleName.USER);
        String provisionalUsername = "user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String placeholderPassword = PLACEHOLDER_PREFIX + UUID.randomUUID();

        User user = User.builder()
                .email(payload.email())
                .username(provisionalUsername)
                .password(placeholderPassword)
                .fullName(payload.name())
                .avatarUrl(payload.picture())
                .status(UserStatus.ACTIVE)
                .oauthProvider(OAuthProvider.GOOGLE)
                .oauthId(payload.sub())
                .role(defaultRole)
                .build();
        user = userRepository.save(user);

        authEventPublisher.publishUserRegisteredGoogle(user.getId(), user.getEmail(), user.getFullName());
        log.info("Created new Google user: userId={} email={}", user.getId(), user.getEmail());
        return user;
    }

    private User activateIfNeeded(User user, GoogleIdTokenPayload payload) {
        boolean dirty = false;
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            user.setStatus(UserStatus.ACTIVE);
            dirty = true;
        }
        if (payload.picture() != null && !payload.picture().isBlank()
                && (user.getAvatarUrl() == null || user.getAvatarUrl().isBlank())) {
            user.setAvatarUrl(payload.picture());
            dirty = true;
        }
        return dirty ? userRepository.save(user) : user;
    }

    private Role lookupRole(RoleName roleName) {
        try {
            return roleLookupService.requireRole(roleName.name());
        } catch (IllegalStateException ex) {
            throw new BusinessException(ErrorCode.SEEDER_ROLE_NOT_FOUND, roleName.name());
        }
    }

    private AuthResponse buildAuthResponse(User user, NextStep nextStep) {
        AuthResponse response = authMapper.toAuthResponse(user, nextStep);
        CustomUserDetails principal = new CustomUserDetails(user);
        response.setAccessToken(jwtTokenProvider.generateAccessToken(principal));
        response.setRefreshToken(jwtTokenProvider.generateRefreshToken(principal));
        response.setExpiresIn(jwtTokenProvider.getAccessTokenExpirationSeconds());
        return response;
    }
}