package com.pwb.iam.api.controller;

import com.pwb.iam.api.dto.request.UpdateProfileRequest;
import com.pwb.iam.api.dto.response.AvatarUploadResponse;
import com.pwb.iam.api.dto.response.ProfileResponse;
import com.pwb.iam.application.command.UpdateAvatarCommand;
import com.pwb.iam.application.command.UpdateProfileCommand;
import com.pwb.iam.application.facade.IamFacade;
import com.pwb.iam.application.facade.ProfileView;
import com.pwb.web.message.MessageResolver;
import com.pwb.web.security.CurrentUser;
import com.pwb.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
public class ProfileController {

    private static final String MSG_PROFILE_RETRIEVED = "PROFILE_RETRIEVED";
    private static final String MSG_PROFILE_UPDATED = "PROFILE_UPDATED";
    private static final String MSG_PROFILE_AVATAR_UPLOADED = "PROFILE_AVATAR_UPLOADED";
    private static final String MSG_UNAUTHORIZED = "AUTH_ACCESS_DENIED";

    private final IamFacade iamFacade;
    private final MessageResolver messageResolver;

    @GetMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile(@CurrentUser UUID userId) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("UNAUTHORIZED", messageResolver.get(MSG_UNAUTHORIZED)));
        }

        ProfileView view = iamFacade.getProfile(userId);
        ProfileResponse response = ProfileResponse.from(view);
        return ResponseEntity.ok(ApiResponse.success(messageResolver.get(MSG_PROFILE_RETRIEVED), response));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> updateProfile(
            @CurrentUser UUID userId,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("UNAUTHORIZED", messageResolver.get(MSG_UNAUTHORIZED)));
        }

        UpdateProfileCommand command = new UpdateProfileCommand(userId, request.fullName());
        ProfileView view = iamFacade.updateProfile(command);
        ProfileResponse response = ProfileResponse.from(view);
        return ResponseEntity.ok(ApiResponse.success(messageResolver.get(MSG_PROFILE_UPDATED), response));
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AvatarUploadResponse>> uploadAvatar(
            @CurrentUser UUID userId,
            @RequestParam("file") MultipartFile file
    ) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("UNAUTHORIZED", messageResolver.get(MSG_UNAUTHORIZED)));
        }

        UpdateAvatarCommand command = new UpdateAvatarCommand(userId, file);
        String avatarUrl = iamFacade.updateAvatar(command);
        AvatarUploadResponse response = AvatarUploadResponse.of(userId, avatarUrl, messageResolver.get(MSG_PROFILE_AVATAR_UPLOADED));
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
