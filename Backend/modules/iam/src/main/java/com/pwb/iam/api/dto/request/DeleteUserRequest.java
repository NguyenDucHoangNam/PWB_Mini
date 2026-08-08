package com.pwb.iam.api.dto.request;

import jakarta.validation.constraints.Size;

public record DeleteUserRequest(
        @Size(max = 500) String reason
) {
}
