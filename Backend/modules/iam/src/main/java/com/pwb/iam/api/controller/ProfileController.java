package com.pwb.iam.api.controller;

import com.pwb.iam.api.dto.request.UpdateProfileRequest;
import com.pwb.iam.api.dto.response.AvatarUploadResponse;
import com.pwb.iam.api.dto.response.ProfileResponse;
import com.pwb.iam.application.command.AvatarUpload;
import com.pwb.iam.application.command.UpdateAvatarCommand;
import com.pwb.iam.application.command.UpdateProfileCommand;
import com.pwb.iam.application.dto.ProfileView;
import com.pwb.iam.application.usecase.GetProfileUseCase;
import com.pwb.iam.application.usecase.UpdateAvatarUseCase;
import com.pwb.iam.application.usecase.UpdateProfileUseCase;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.shared.dto.ApiResponse;
import com.pwb.shared.exception.BusinessException;
import com.pwb.web.message.MessageResolver;
import com.pwb.web.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * Endpoints under {@code /api/v1/profile}. None of them are public, so Spring Security rejects
 * anonymous callers before the handler runs and {@code @CurrentUser} is never null here.
 */
@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
public class ProfileController {

    private static final String MSG_PROFILE_RETRIEVED = "PROFILE_RETRIEVED";
    private static final String MSG_PROFILE_UPDATED = "PROFILE_UPDATED";
    private static final String MSG_PROFILE_AVATAR_UPLOADED = "PROFILE_AVATAR_UPLOADED";

    private final GetProfileUseCase getProfileUseCase;
    private final UpdateProfileUseCase updateProfileUseCase;
    private final UpdateAvatarUseCase updateAvatarUseCase;
    private final MessageResolver messageResolver;

    @GetMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile(@CurrentUser UUID userId) {
        ProfileView view = getProfileUseCase.execute(userId);
        return ResponseEntity.ok(ApiResponse.success(
                messageResolver.get(MSG_PROFILE_RETRIEVED), ProfileResponse.from(view)));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> updateProfile(
            @CurrentUser UUID userId,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        ProfileView view = updateProfileUseCase.execute(new UpdateProfileCommand(userId, request.fullName()));
        return ResponseEntity.ok(ApiResponse.success(
                messageResolver.get(MSG_PROFILE_UPDATED), ProfileResponse.from(view)));
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AvatarUploadResponse>> uploadAvatar(
            @CurrentUser UUID userId,
            @RequestParam("file") MultipartFile file
    ) {
        UpdateAvatarCommand command = new UpdateAvatarCommand(userId, toUpload(file));
        String avatarUrl = updateAvatarUseCase.execute(command);
        AvatarUploadResponse response = AvatarUploadResponse.of(
                userId, avatarUrl, messageResolver.get(MSG_PROFILE_AVATAR_UPLOADED));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Adapts Spring's multipart type into the framework-free {@link AvatarUpload} the application
     * layer works with. Only the leading bytes are read here — for format sniffing — while the
     * body itself stays lazy so it is streamed to storage rather than buffered on the heap.
     */
    private AvatarUpload toUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(IamErrorCode.PROFILE_INVALID_AVATAR_FORMAT);
        }
        byte[] header = new byte[AvatarUpload.HEADER_BYTES];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(header, 0, header.length);
        } catch (IOException ex) {
            throw new BusinessException(IamErrorCode.PROFILE_INVALID_AVATAR_FORMAT, ex);
        }
        byte[] actualHeader = read == header.length ? header : java.util.Arrays.copyOf(header, Math.max(read, 0));

        return new AvatarUpload(
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                actualHeader,
                file::getInputStream
        );
    }
}
