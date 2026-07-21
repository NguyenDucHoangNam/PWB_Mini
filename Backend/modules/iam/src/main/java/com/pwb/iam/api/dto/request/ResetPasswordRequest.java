package com.pwb.iam.api.dto.request;

import com.pwb.iam.infrastructure.security.annotation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequest {

    @NotBlank
    @Size(min = 16, max = 1024)
    private String token;

    @NotBlank
    @Size(max = 128)
    @StrongPassword
    private String newPassword;
}