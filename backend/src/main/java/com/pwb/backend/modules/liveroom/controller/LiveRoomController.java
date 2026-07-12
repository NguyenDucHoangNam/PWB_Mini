package com.pwb.backend.modules.liveroom.controller;

import com.pwb.backend.common.dto.ApiResponse;
import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.common.security.CurrentUserResolver;
import com.pwb.backend.modules.iam.exception.IamErrorCode;
import com.pwb.backend.modules.iam.model.User;
import com.pwb.backend.modules.iam.repository.UserRepository;
import com.pwb.backend.modules.liveroom.dto.request.CreateRoomRequest;
import com.pwb.backend.modules.liveroom.dto.response.CreateRoomResponse;
import com.pwb.backend.modules.liveroom.service.RoomLifecycleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class LiveRoomController {

    private static final String MSG_ROOM_CREATED = "LIVE_ROOM_CREATED";

    private final RoomLifecycleService roomLifecycleService;
    private final CurrentUserResolver currentUserResolver;
    private final UserRepository userRepository;
    private final MessageSource messageSource;

    @PostMapping
    @PreAuthorize("hasRole('USER_PRO')")
    public ResponseEntity<ApiResponse<CreateRoomResponse>> createRoom(
            @Valid @RequestBody CreateRoomRequest request) {
        UUID hostId = currentUserResolver.resolveUserId();
        User host = userRepository.findById(hostId)
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND,
                        "Host " + hostId + " not found"));
        String hostDisplayName = host.getFullName();
        if (hostDisplayName == null || hostDisplayName.isBlank()) {
            hostDisplayName = host.getEmail();
        }
        CreateRoomResponse data = roomLifecycleService.createRoom(request.mode(), hostId, hostDisplayName);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(message(MSG_ROOM_CREATED), data));
    }

    private String message(String key, Object... args) {
        return messageSource.getMessage(key, args, key, LocaleContextHolder.getLocale());
    }
}