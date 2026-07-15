package com.pwb.backend.service.impl;

import com.pwb.backend.dto.request.ChangePasswordRequest;
import com.pwb.backend.dto.response.AuthMessageResponse;
import com.pwb.backend.entity.rdbms.User;
import com.pwb.backend.enums.OAuthProvider;
import com.pwb.backend.exception.BusinessException;
import com.pwb.backend.exception.ErrorCode;
import com.pwb.backend.repository.rdbms.UserRepository;
import com.pwb.backend.service.AuthEventPublisher;
import com.pwb.backend.service.ChangePasswordService;
import com.pwb.backend.utils.helper.MessageHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChangePasswordServiceImpl implements ChangePasswordService {

    private static final String MSG_CHANGED = "auth.password_changed.success";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthEventPublisher authEventPublisher;
    private final MessageHelper messageHelper;

    @Override
    @Transactional
    public AuthMessageResponse changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getOauthProvider() != OAuthProvider.LOCAL) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH_USER_NO_PASSWORD);
        }

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CURRENT_PASSWORD);
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.AUTH_PASSWORD_REUSED);
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        authEventPublisher.publishPasswordChanged(user.getId(), user.getEmail());

        log.info("Password changed: userId={}", user.getId());
        return AuthMessageResponse.of(user.getId(), messageHelper.get(MSG_CHANGED));
    }
}