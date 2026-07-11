package com.pwb.backend.modules.iam.service;

public record AnonymizationReport(
        int processedCount,
        long durationMs,
        String status
) {
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_EMPTY = "EMPTY";
    public static final String STATUS_FAILED = "FAILED";
}