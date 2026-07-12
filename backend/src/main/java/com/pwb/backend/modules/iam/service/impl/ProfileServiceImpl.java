package com.pwb.backend.modules.iam.service.impl;

import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.common.util.MaskingLogArg;
import com.pwb.backend.modules.iam.dto.request.UpdateProfileRequest;
import com.pwb.backend.modules.iam.dto.response.UserProfileResponse;
import com.pwb.backend.modules.iam.enums.UserStatus;
import com.pwb.backend.modules.iam.exception.IamErrorCode;
import com.pwb.backend.modules.iam.mapper.UserMapper;
import com.pwb.backend.modules.iam.model.User;
import com.pwb.backend.modules.iam.repository.UserRepository;
import com.pwb.backend.modules.iam.service.ProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileServiceImpl implements ProfileService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));
        log.info("PROFILE_FETCH_SUCCESS userId={}", userId);
        return userMapper.toUserProfileResponse(user);
    }

    @Override
    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));

        if (user.getStatus() == UserStatus.BANNED) {
            throw new BusinessException(IamErrorCode.ACCOUNT_BANNED);
        }

        List<String> updatedFields = new ArrayList<>();
        boolean changed = false;

        String newFullName = request.fullName() == null ? null : request.fullName().trim();
        if (newFullName != null && !newFullName.equals(user.getFullName())) {
            user.setFullName(newFullName);
            updatedFields.add("fullName");
            changed = true;
        }

        String newPhone = request.phone() == null || request.phone().isBlank() ? null : request.phone().trim();
        if (!Objects.equals(newPhone, user.getPhone())) {
            user.setPhone(newPhone);
            updatedFields.add("phone");
            changed = true;
        }

        String newAvatar = request.avatarUrl() == null || request.avatarUrl().isBlank() ? null : request.avatarUrl().trim();
        if (!Objects.equals(newAvatar, user.getAvatarUrl())) {
            user.setAvatarUrl(newAvatar);
            updatedFields.add("avatarUrl");
            changed = true;
        }

        if (!changed) {
            log.info("PROFILE_UPDATE_NOOP userId={}", userId);
            return userMapper.toUserProfileResponse(user);
        }

        userRepository.save(user);
        log.info("PROFILE_UPDATE_SUCCESS userId={} updatedFields={} email={}",
                userId, updatedFields, MaskingLogArg.email(user.getEmail()));
        return userMapper.toUserProfileResponse(user);
    }
}
