package com.pwb.backend.iam.api.dto.response;

public record TriggerAnonymizationResponse(
    int processedUsersCount,
    long executionTimeMs,
    String status
) {}
