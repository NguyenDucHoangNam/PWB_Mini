package com.pwb.iam.api.dto.request;

import com.pwb.iam.infrastructure.security.annotation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteProfileRequest {

    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9_-]{3,50}$")
    private String username;

    @Size(max = 100)
    private String fullName;

    @Size(max = 128)
    @StrongPassword
    private String newPassword;
}