package com.pwb.backend.modules.share.service;

import com.pwb.backend.modules.share.dto.request.DistributeDemoRequest;
import com.pwb.backend.modules.share.dto.response.DistributeDemoResponse;
import com.pwb.backend.modules.share.dto.response.DistributionListItemResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface DemoDistributionService {

    DistributeDemoResponse distribute(UUID demoId, DistributeDemoRequest request, UUID producerId);

    Page<DistributionListItemResponse> listDistributions(UUID demoId, UUID producerId,
                                                        boolean includeRevoked, Pageable pageable);

    List<String> suggestRecipients(String keyword, UUID producerId);
}