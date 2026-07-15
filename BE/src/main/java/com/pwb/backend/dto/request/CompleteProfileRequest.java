package com.pwb.backend.dto.request;

import com.pwb.backend.utils.validation.annotation.ValidPassword;
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
    @Pattern(regexp = "^[a-zA-Z0-9_-]{3,50}$", message = "{error.user.name_invalid}")
    private String username;

    @Size(max = 100)
    private String fullName;

    @ValidPassword
    private String newPassword;
}
