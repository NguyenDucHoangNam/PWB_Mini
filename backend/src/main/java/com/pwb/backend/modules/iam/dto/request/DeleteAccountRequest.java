package com.pwb.backend.modules.iam.dto.request;

import jakarta.validation.constraints.Size;

public record DeleteAccountRequest(

        @Size(max = 200, message = "Password must not exceed 200 characters")
        String password,

        @Size(max = 4096, message = "idToken must not exceed 4096 characters")
        String idToken
) {
}
