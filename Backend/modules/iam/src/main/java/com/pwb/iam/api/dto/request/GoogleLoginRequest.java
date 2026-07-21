package com.pwb.iam.api.dto.request;

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
public class GoogleLoginRequest {

    @NotBlank(message = "{validation.google.idtoken.required}")
    @Size(min = 10, max = 4096, message = "{validation.google.idtoken.size}")
    private String idToken;
}