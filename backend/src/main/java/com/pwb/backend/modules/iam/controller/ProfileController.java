package com.pwb.backend.modules.iam.controller;

import com.pwb.backend.common.dto.ApiResponse;
import com.pwb.backend.common.security.CurrentUserResolver;
import com.pwb.backend.modules.iam.dto.request.UpdateProfileRequest;
import com.pwb.backend.modules.iam.dto.response.AvatarUploadResponse;
import com.pwb.backend.modules.iam.dto.response.UserProfileResponse;
import com.pwb.backend.modules.iam.service.AvatarUploadService;
import com.pwb.backend.modules.iam.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class ProfileController {

    private static final String MSG_PROFILE_FETCHED = "PROFILE_FETCHED";
    private static final String MSG_PROFILE_UPDATED = "PROFILE_UPDATED";
    private static final String MSG_AVATAR_UPLOADED = "AVATAR_UPLOADED";

    private final ProfileService profileService;
    private final AvatarUploadService avatarUploadService;
    private final CurrentUserResolver currentUserResolver;
    private final MessageSource messageSource;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> me() {
        UUID userId = currentUserResolver.resolveUserId();
        UserProfileResponse data = profileService.getProfile(userId);
        return ResponseEntity.ok(ApiResponse.success(message(MSG_PROFILE_FETCHED), data));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        UUID userId = currentUserResolver.resolveUserId();
        UserProfileResponse data = profileService.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success(message(MSG_PROFILE_UPDATED), data));
    }

    @PostMapping(value = "/profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AvatarUploadResponse>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        UUID userId = currentUserResolver.resolveUserId();
        AvatarUploadResponse data = avatarUploadService.uploadAvatar(userId, file);
        return ResponseEntity.ok(ApiResponse.success(message(MSG_AVATAR_UPLOADED), data));
    }

    private String message(String key) {
        return messageSource.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }
}