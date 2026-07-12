package com.pwb.backend.modules.iam.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.common.exception.CommonErrorCode;
import com.pwb.backend.common.exception.GlobalExceptionHandler;
import com.pwb.backend.common.security.CurrentUserResolver;
import com.pwb.backend.modules.iam.dto.request.UpdateProfileRequest;
import com.pwb.backend.modules.iam.dto.response.AvatarUploadResponse;
import com.pwb.backend.modules.iam.dto.response.UserProfileResponse;
import com.pwb.backend.modules.iam.service.AvatarUploadService;
import com.pwb.backend.modules.iam.service.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private ProfileService profileService;

    @Mock
    private AvatarUploadService avatarUploadService;

    @Mock
    private CurrentUserResolver currentUserResolver;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private ProfileController profileController;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(profileController)
                .setControllerAdvice(new GlobalExceptionHandler(messageSource))
                .build();
    }

    @Test
    void me_success() throws Exception {
        UserProfileResponse response = new UserProfileResponse(
                userId, "test@example.com", "Full Name", "USER", "ACTIVE", "avatar", "phone", null
        );

        when(currentUserResolver.resolveUserId()).thenReturn(userId);
        when(profileService.getProfile(userId)).thenReturn(response);
        when(messageSource.getMessage(anyString(), any(), any(), any())).thenReturn("Profile fetched");

        mockMvc.perform(get("/api/v1/auth/me"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("test@example.com"));
    }

    @Test
    void updateProfile_success() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest("New Name", "0987654321", "avatar");
        UserProfileResponse response = new UserProfileResponse(
                userId, "test@example.com", "New Name", "USER", "ACTIVE", "avatar", "0987654321", null
        );

        when(currentUserResolver.resolveUserId()).thenReturn(userId);
        when(profileService.updateProfile(eq(userId), any())).thenReturn(response);
        when(messageSource.getMessage(anyString(), any(), any(), any())).thenReturn("Profile updated");

        mockMvc.perform(put("/api/v1/auth/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fullName").value("New Name"));
    }

    @Test
    void uploadAvatar_success() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", "dummy-bytes".getBytes());
        AvatarUploadResponse response = new AvatarUploadResponse("http://localhost/avatars/new.png", 100L, "image/png");

        when(currentUserResolver.resolveUserId()).thenReturn(userId);
        when(avatarUploadService.uploadAvatar(eq(userId), any())).thenReturn(response);
        when(messageSource.getMessage(anyString(), any(), any(), any())).thenReturn("Avatar uploaded");

        mockMvc.perform(multipart("/api/v1/auth/profile/avatar").file(file))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.avatarUrl").value("http://localhost/avatars/new.png"));
    }

    @Test
    void me_whenResolverThrows_throwsUnauthorized() throws Exception {
        doThrow(new BusinessException(CommonErrorCode.UNAUTHORIZED))
                .when(currentUserResolver).resolveUserId();

        mockMvc.perform(get("/api/v1/auth/me"))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }
}
