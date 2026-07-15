package com.pwb.backend.dto.request;

import com.pwb.backend.utils.validation.annotation.ValidPassword;
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
    @ValidPassword
    private String newPassword;
}