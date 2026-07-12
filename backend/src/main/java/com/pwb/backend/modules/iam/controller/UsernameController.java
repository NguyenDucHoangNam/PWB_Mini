package com.pwb.backend.modules.iam.controller;

import com.pwb.backend.common.dto.ApiResponse;
import com.pwb.backend.modules.iam.dto.response.CheckUsernameResponse;
import com.pwb.backend.modules.iam.service.UsernameAvailabilityService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Validated
public class UsernameController {

    private static final String MSG_USERNAME_AVAILABILITY_CHECKED = "AUTH_USERNAME_AVAILABILITY_CHECKED";
    private static final String USERNAME_PATTERN = "^[a-zA-Z0-9_]+$";
    private static final int USERNAME_MIN_LENGTH = 3;
    private static final int USERNAME_MAX_LENGTH = 50;

    private final UsernameAvailabilityService usernameAvailabilityService;
    private final MessageSource messageSource;

    @GetMapping("/check-username")
    public ResponseEntity<ApiResponse<CheckUsernameResponse>> checkUsername(
            @RequestParam("username")
            @NotBlank(message = "{validation.username.required}")
            @Size(min = USERNAME_MIN_LENGTH, max = USERNAME_MAX_LENGTH,
                  message = "{validation.username.length}")
            @Pattern(regexp = USERNAME_PATTERN, message = "{validation.username.format}")
            String rawUsername) {

        String username = rawUsername.trim();
        boolean available = usernameAvailabilityService.isAvailable(username);
        return ResponseEntity.ok(ApiResponse.success(
                message(MSG_USERNAME_AVAILABILITY_CHECKED),
                new CheckUsernameResponse(username, available)));
    }

    private String message(String key, Object... args) {
        return messageSource.getMessage(key, args, key, LocaleContextHolder.getLocale());
    }
}