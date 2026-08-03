package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.GoogleLoginCommand;
import com.pwb.iam.application.service.AccountNotifier;
import com.pwb.iam.application.service.RateLimitGuard;
import com.pwb.iam.application.usecase.GoogleLoginUseCase;
import com.pwb.iam.application.usecase.LoginResult;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.model.EmailAddress;
import com.pwb.iam.domain.model.GoogleUserInfo;
import com.pwb.iam.domain.model.LoginPolicy;
import com.pwb.iam.domain.model.OAuthProvider;
import com.pwb.iam.domain.model.Role;
import com.pwb.iam.domain.model.RoleName;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.model.UserStatus;
import com.pwb.iam.domain.repository.RoleRepository;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.EmailDeliveryPort;
import com.pwb.iam.domain.service.EmailEnqueueCommand;
import com.pwb.iam.domain.service.GoogleTokenVerifierPort;
import com.pwb.iam.domain.service.TokenManagerService;
import com.pwb.infra.mail.api.EmailTemplate;
import com.pwb.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleLoginUseCaseImpl implements GoogleLoginUseCase {

    private static final String FALLBACK_DISPLAY_NAME = "bạn";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final GoogleTokenVerifierPort googleTokenVerifier;
    private final TokenManagerService tokenManagerService;
    private final AuthEventPublisher authEventPublisher;
    private final RateLimitGuard rateLimitGuard;
    private final LoginPolicy loginPolicy;
    private final EmailDeliveryPort emailDeliveryPort;
    private final AccountNotifier accountNotifier;

    @Override
    @Transactional
    public LoginResult execute(GoogleLoginCommand command) {
        String clientIp = command.clientIp();
        String userAgent = command.userAgent();

        GoogleUserInfo payload;
        try {
            payload = googleTokenVerifier.verify(command.idToken());
        } catch (BusinessException ex) {
            authEventPublisher.publishGoogleLoginFailed("unknown", clientIp, userAgent, ex.getMessage());
            throw ex;
        }

        rateLimitGuard.checkIpAndSubject(
                "google-login", clientIp, payload.email(), loginPolicy.googleLoginPerMinute());

        User user = userRepository.findByOAuthProviderAndOAuthId(OAuthProvider.GOOGLE, payload.sub())
                .map(existing -> refreshFromGoogle(existing, payload))
                .orElseGet(() -> linkOrCreate(payload, command.locale()));

        if (user.isBlocked()) {
            throw new BusinessException(IamErrorCode.ACCOUNT_INACTIVE);
        }

        TokenManagerService.AccessTokenInfo access = tokenManagerService.issueAccessToken(user);
        TokenManagerService.RefreshTokenInfo refresh = tokenManagerService.issueRefreshToken(user.getUserId());

        authEventPublisher.publishGoogleLoginSuccess(
                user.getUserId(), user.getEmail().value(), clientIp, userAgent);

        log.info("Google login success: userId={}", user.getUserId());
        return new LoginResult(user, access, refresh);
    }

    /**
     * No Google link yet: either the address already exists locally (link the two) or this is a
     * brand new account.
     */
    private User linkOrCreate(GoogleUserInfo payload, String locale) {
        User byEmail = userRepository.findByEmail(payload.email()).orElse(null);
        if (byEmail == null) {
            return createFromGoogle(payload, locale);
        }

        if (byEmail.isBlocked()) {
            throw new BusinessException(IamErrorCode.ACCOUNT_INACTIVE);
        }
        byEmail.linkOAuth(OAuthProvider.GOOGLE, payload.sub());
        applyGoogleDefaults(byEmail, payload);
        if (byEmail.getStatus() == UserStatus.PENDING_VERIFICATION) {
            // Google already vouched for the address, so the pending OTP is moot.
            byEmail.markActive();
        }

        User saved = userRepository.save(byEmail);
        authEventPublisher.publishUserLinkedGoogle(
                saved.getUserId(), saved.getEmail().value(), saved.getFullName());
        // Linking gives a second way into an existing account, so the holder is told about it.
        accountNotifier.googleAccountLinked(saved, locale);
        log.info("Linked Google account to existing user: userId={}", saved.getUserId());
        return saved;
    }

    private User createFromGoogle(GoogleUserInfo payload, String locale) {
        RoleName roleName = roleRepository.findByName(RoleName.USER)
                .map(Role::getName)
                .orElseThrow(() -> new BusinessException(IamErrorCode.ROLE_NOT_FOUND));

        User fresh = User.createGoogle(
                EmailAddress.of(payload.email()),
                payload.sub(),
                payload.name(),
                payload.picture());
        fresh.assignRole(roleName);
        fresh.markActive();

        User saved = userRepository.save(fresh);
        authEventPublisher.publishUserRegisteredGoogle(
                saved.getUserId(), saved.getEmail().value(), saved.getFullName());
        enqueueWelcomeEmail(saved, locale);
        log.info("Registered new Google user: userId={}", saved.getUserId());
        return saved;
    }

    private User refreshFromGoogle(User user, GoogleUserInfo payload) {
        boolean changed = false;
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            user.markActive();
            changed = true;
        }
        changed |= applyGoogleDefaults(user, payload);
        return changed ? userRepository.save(user) : user;
    }

    /**
     * Google values only fill gaps — a name or avatar the user set here is never overwritten.
     */
    private boolean applyGoogleDefaults(User user, GoogleUserInfo payload) {
        boolean changed = false;
        if (isPresent(payload.picture()) && !isPresent(user.getAvatarUrl())) {
            user.changeAvatarUrl(payload.picture());
            changed = true;
        }
        if (isPresent(payload.name()) && !isPresent(user.getFullName())) {
            user.changeFullName(payload.name());
            changed = true;
        }
        return changed;
    }

    private void enqueueWelcomeEmail(User user, String locale) {
        String displayName = isPresent(user.getFullName()) ? user.getFullName() : FALLBACK_DISPLAY_NAME;
        emailDeliveryPort.enqueue(new EmailEnqueueCommand(
                user.getUserId(),
                user.getEmail().value(),
                EmailTemplate.WELCOME_GOOGLE,
                Map.of("displayName", displayName),
                locale
        ));
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
