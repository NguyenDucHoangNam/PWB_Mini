package com.pwb.backend.modules.iam.service.impl;

import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.common.storage.ObjectStorageService;
import com.pwb.backend.common.storage.StorageBucket;
import com.pwb.backend.modules.iam.dto.response.AvatarUploadResponse;
import com.pwb.backend.modules.iam.enums.UserStatus;
import com.pwb.backend.modules.iam.exception.IamErrorCode;
import com.pwb.backend.modules.iam.model.Role;
import com.pwb.backend.modules.iam.model.User;
import com.pwb.backend.modules.iam.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AvatarUploadServiceImplTest {

    @Mock
    private ObjectStorageService objectStorageService;

    @Mock
    private UserRepository userRepository;

    private AvatarUploadServiceImpl avatarUploadService;

    private final UUID userId = UUID.randomUUID();
    private User activeUser;

    @BeforeEach
    void setUp() {
        avatarUploadService = new AvatarUploadServiceImpl(objectStorageService, userRepository);
        ReflectionTestUtils.setField(avatarUploadService, "maxSizeBytes", 2097152L);

        Role role = new Role(UUID.randomUUID(), "USER", "User");
        activeUser = User.newPending("test@example.com", "hash", "Test Name", role);
        activeUser.setStatus(UserStatus.ACTIVE);
        ReflectionTestUtils.setField(activeUser, "id", userId);
    }

    private byte[] createDummyImage(String format) throws IOException {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        return baos.toByteArray();
    }

    @Test
    void uploadAvatar_success_png() throws Exception {
        byte[] bytes = createDummyImage("png");
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", bytes
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));
        ObjectStorageService.StoredObject stored = new ObjectStorageService.StoredObject(
                "pwb-avatars", "avatars/new.png", "http://localhost/avatars/new.png", (long) bytes.length, "image/png"
        );
        when(objectStorageService.putObject(eq(StorageBucket.AVATAR), anyString(), any(byte[].class), eq("image/png"), anyMap()))
                .thenReturn(stored);

        AvatarUploadResponse response = avatarUploadService.uploadAvatar(userId, file);

        assertNotNull(response);
        assertEquals("http://localhost/avatars/new.png", response.avatarUrl());
        verify(userRepository).save(activeUser);
    }

    @Test
    void uploadAvatar_success_jpeg() throws Exception {
        byte[] bytes = createDummyImage("jpeg");
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg", bytes
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));
        ObjectStorageService.StoredObject stored = new ObjectStorageService.StoredObject(
                "pwb-avatars", "avatars/new.jpg", "http://localhost/avatars/new.jpg", (long) bytes.length, "image/jpeg"
        );
        when(objectStorageService.putObject(eq(StorageBucket.AVATAR), anyString(), any(byte[].class), eq("image/jpeg"), anyMap()))
                .thenReturn(stored);

        AvatarUploadResponse response = avatarUploadService.uploadAvatar(userId, file);

        assertNotNull(response);
        assertEquals("http://localhost/avatars/new.jpg", response.avatarUrl());
    }

    @Test
    void uploadAvatar_fileEmpty() {
        MockMultipartFile file = new MockMultipartFile("file", "", "image/png", new byte[0]);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                avatarUploadService.uploadAvatar(userId, file)
        );
        assertEquals(IamErrorCode.AVATAR_FILE_EMPTY, exception.getErrorCode());
    }

    @Test
    void uploadAvatar_fileTooLarge() {
        byte[] bytes = new byte[3000000]; // 3MB > 2MB
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", bytes);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                avatarUploadService.uploadAvatar(userId, file)
        );
        assertEquals(IamErrorCode.AVATAR_FILE_TOO_LARGE, exception.getErrorCode());
    }

    @Test
    void uploadAvatar_invalidContentType() {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.txt", "text/plain", "hello".getBytes());

        BusinessException exception = assertThrows(BusinessException.class, () ->
                avatarUploadService.uploadAvatar(userId, file)
        );
        assertEquals(IamErrorCode.AVATAR_INVALID_TYPE, exception.getErrorCode());
    }

    @Test
    void deleteAvatar_success() {
        activeUser.setAvatarUrl("http://localhost/avatars/old.png");
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));

        avatarUploadService.deleteAvatar(userId);

        verify(objectStorageService).deleteObject(StorageBucket.AVATAR, "avatars/old.png");
        assertNull(activeUser.getAvatarUrl());
        verify(userRepository).save(activeUser);
    }

    @Test
    void uploadAvatar_userNotFound() {
        byte[] bytes = new byte[100];
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", bytes);

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () ->
                avatarUploadService.uploadAvatar(userId, file)
        );
        assertEquals(IamErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void uploadAvatar_storageServiceFails() throws Exception {
        byte[] bytes = createDummyImage("png");
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", bytes);

        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));
        when(objectStorageService.putObject(eq(StorageBucket.AVATAR), anyString(), any(byte[].class), eq("image/png"), anyMap()))
                .thenThrow(new RuntimeException("S3 connection failed"));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                avatarUploadService.uploadAvatar(userId, file)
        );
        assertEquals(IamErrorCode.AVATAR_UPLOAD_FAILED, exception.getErrorCode());
    }

    @Test
    void deleteAvatar_whenAvatarUrlNull() {
        activeUser.setAvatarUrl(null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));

        avatarUploadService.deleteAvatar(userId);

        verify(objectStorageService, never()).deleteObject(any(), anyString());
        verify(userRepository, never()).save(any());
    }
}
