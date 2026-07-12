package com.pwb.backend.modules.iam.dto.request;

import jakarta.validation.constraints.Size;

public record DeleteAccountRequest(

        @Size(max = 200, message = "{validation.password.length}")
        String password,

        @Size(max = 4096, message = "{validation.token.required}")
        String idToken
) {
}
