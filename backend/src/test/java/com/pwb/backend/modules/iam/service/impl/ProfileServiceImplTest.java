package com.pwb.backend.modules.iam.service.impl;

import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.modules.iam.dto.request.UpdateProfileRequest;
import com.pwb.backend.modules.iam.dto.response.UserProfileResponse;
import com.pwb.backend.modules.iam.enums.UserStatus;
import com.pwb.backend.modules.iam.exception.IamErrorCode;
import com.pwb.backend.modules.iam.mapper.UserMapper;
import com.pwb.backend.modules.iam.model.Role;
import com.pwb.backend.modules.iam.model.User;
import com.pwb.backend.modules.iam.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    private ProfileServiceImpl profileService;

    private final UUID userId = UUID.randomUUID();
    private User activeUser;

    @BeforeEach
    void setUp() {
        profileService = new ProfileServiceImpl(userRepository, userMapper);

        Role role = new Role(UUID.randomUUID(), "USER", "User");
        activeUser = User.newPending("test@example.com", "hash", "Old Name", role);
        activeUser.setStatus(UserStatus.ACTIVE);
        activeUser.setPhone("0123456789");
        activeUser.setAvatarUrl("old_avatar_url");
        ReflectionTestUtils.setField(activeUser, "id", userId);
    }

    @Test
    void getProfile_success() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));
        when(userMapper.toUserProfileResponse(activeUser))
                .thenReturn(new UserProfileResponse(userId, "test@example.com", "Old Name", "USER", "ACTIVE", "old_avatar_url", "0123456789", null));

        UserProfileResponse response = profileService.getProfile(userId);

        assertNotNull(response);
        assertEquals("Old Name", response.fullName());
    }

    @Test
    void getProfile_userNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () -> profileService.getProfile(userId));
        assertEquals(IamErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void updateProfile_success() {
        UpdateProfileRequest request = new UpdateProfileRequest("New Name", "0987654321", "new_avatar_url");
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));
        when(userMapper.toUserProfileResponse(activeUser))
                .thenReturn(new UserProfileResponse(userId, "test@example.com", "New Name", "USER", "ACTIVE", "new_avatar_url", "0987654321", null));

        UserProfileResponse response = profileService.updateProfile(userId, request);

        assertNotNull(response);
        assertEquals("New Name", activeUser.getFullName());
        assertEquals("0987654321", activeUser.getPhone());
        assertEquals("new_avatar_url", activeUser.getAvatarUrl());
        verify(userRepository).save(activeUser);
    }

    @Test
    void updateProfile_noop() {
        UpdateProfileRequest request = new UpdateProfileRequest("Old Name", "0123456789", "old_avatar_url");
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));
        when(userMapper.toUserProfileResponse(activeUser))
                .thenReturn(new UserProfileResponse(userId, "test@example.com", "Old Name", "USER", "ACTIVE", "old_avatar_url", "0123456789", null));

        UserProfileResponse response = profileService.updateProfile(userId, request);

        assertNotNull(response);
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfile_userBanned() {
        activeUser.setStatus(UserStatus.BANNED);
        UpdateProfileRequest request = new UpdateProfileRequest("New Name", "0987654321", "new_avatar_url");
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));

        BusinessException exception = assertThrows(BusinessException.class, () -> profileService.updateProfile(userId, request));
        assertEquals(IamErrorCode.ACCOUNT_BANNED, exception.getErrorCode());
    }
}
