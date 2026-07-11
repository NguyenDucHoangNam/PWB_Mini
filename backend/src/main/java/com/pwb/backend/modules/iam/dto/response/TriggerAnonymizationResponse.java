package com.pwb.backend.modules.iam.dto.response;

public record TriggerAnonymizationResponse(
        int processedUsersCount,
        long executionTimeMs,
        String status
) {
}