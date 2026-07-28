package com.pwb.iam.application.usecase.impl;

import com.pwb.kernel.exception.BusinessException;
import com.pwb.kernel.exception.SysErrorCode;
import com.pwb.iam.domain.exception.IamErrorCode;

import com.pwb.iam.api.dto.GoogleIdTokenPayload;
import com.pwb.iam.application.command.GoogleLoginCommand;
import com.pwb.iam.application.usecase.GoogleLoginUseCase;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.model.EmailAddress;
import com.pwb.iam.domain.model.OAuthProvider;
import com.pwb.iam.domain.model.Role;
import com.pwb.iam.domain.model.RoleName;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.model.UserStatus;
import com.pwb.iam.domain.repository.RoleRepository;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.GoogleTokenVerifierPort;
import com.pwb.iam.domain.service.TokenResult;
import com.pwb.iam.domain.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleLoginUseCaseImpl implements GoogleLoginUseCase {

    private static final int PROVISIONAL_USERNAME_RANDOM_LENGTH = 16;
    private static final String PROVISIONAL_USERNAME_PREFIX = "user_";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final GoogleTokenVerifierPort googleTokenVerifier;
    private final TokenService tokenService;
    private final AuthEventPublisher authEventPublisher;

    @Override
    @Transactional
    public TokenResult execute(GoogleLoginCommand command) {
        GoogleIdTokenPayload payload = googleTokenVerifier.verify(command.idToken());

        User user = userRepository.findByOAuthProviderAndOAuthId(OAuthProvider.GOOGLE, payload.sub())
                .orElse(null);

        if (user == null) {
            user = handleNewGoogleUser(payload);
        } else {
            user = handleExistingGoogleUser(user, payload);
        }

        if (user.getStatus() == UserStatus.BANNED || user.getStatus() == UserStatus.DELETED) {
            throw new BusinessException(SysErrorCode.FORBIDDEN);
        }

        authEventPublisher.publishLoginSuccess(user.getUserId(), user.getEmail().value());
        log.info("Google login success: userId={} email={}", user.getUserId(), user.getEmail().value());

        TokenResult.NextStep nextStep = user.isProvisionalUsername()
                ? TokenResult.NextStep.COMPLETE_PROFILE
                : TokenResult.NextStep.NONE;

        return tokenService.buildAuthTokens(user, nextStep);
    }

    private User handleNewGoogleUser(GoogleIdTokenPayload payload) {
        User byEmail = userRepository.findByEmail(payload.email()).orElse(null);

        if (byEmail != null) {
            byEmail.linkOAuth(OAuthProvider.GOOGLE, payload.sub());
            if (payload.picture() != null && !payload.picture().isBlank()
                    && (byEmail.getAvatarUrl() == null || byEmail.getAvatarUrl().isBlank())) {
                byEmail.changeAvatarUrl(payload.picture());
            }
            if (payload.name() != null && !payload.name().isBlank()
                    && (byEmail.getFullName() == null || byEmail.getFullName().isBlank())) {
                byEmail.changeFullName(payload.name());
            }
            if (byEmail.getStatus() == UserStatus.PENDING_VERIFICATION) {
                byEmail.markActive();
            }
            User saved = userRepository.save(byEmail);
            authEventPublisher.publishUserLinkedGoogle(
                    saved.getUserId(), saved.getEmail().value(), saved.getFullName());
            return saved;
        }

        String username = generateProvisionalUsername();
        User fresh = User.createGoogle(
                username,
                EmailAddress.of(payload.email()),
                payload.sub(),
                payload.name(),
                payload.picture());

        Role role = roleRepository.findByName(RoleName.USER.name())
                .orElseThrow(() -> new BusinessException(IamErrorCode.SEEDER_ROLE_NOT_FOUND));
        fresh.assignRole(role);
        fresh.markActive();

        User saved = userRepository.save(fresh);
        authEventPublisher.publishUserRegisteredGoogle(
                saved.getUserId(), saved.getEmail().value(), saved.getFullName());
        return saved;
    }

    private User handleExistingGoogleUser(User user, GoogleIdTokenPayload payload) {
        boolean dirty = false;
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            user.markActive();
            dirty = true;
        }
        if (payload.picture() != null && !payload.picture().isBlank()
                && (user.getAvatarUrl() == null || user.getAvatarUrl().isBlank())) {
            user.changeAvatarUrl(payload.picture());
            dirty = true;
        }
        if (dirty) {
            return userRepository.save(user);
        }
        return user;
    }

    private String generateProvisionalUsername() {
        String hex = UUID.randomUUID().toString().replace("-", "");
        return PROVISIONAL_USERNAME_PREFIX + hex.substring(0, PROVISIONAL_USERNAME_RANDOM_LENGTH);
    }
}
