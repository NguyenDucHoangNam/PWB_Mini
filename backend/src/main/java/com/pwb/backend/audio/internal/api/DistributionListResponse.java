package com.pwb.backend.audio.internal.api;

import java.util.List;

public record DistributionListResponse(
    List<DistributionListItem> items,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext
) {}
