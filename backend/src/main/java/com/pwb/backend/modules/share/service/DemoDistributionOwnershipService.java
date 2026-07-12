package com.pwb.backend.modules.share.service;

import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.modules.audio.entity.Demo;
import com.pwb.backend.modules.audio.enums.DemoStatus;
import com.pwb.backend.modules.audio.repository.DemoRepository;
import com.pwb.backend.modules.share.exception.ShareErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DemoDistributionOwnershipService {

    private final DemoRepository demoRepository;

    public Demo assertActiveAndOwned(UUID demoId, UUID producerId) {
        Demo demo = demoRepository.findById(demoId)
                .orElseThrow(() -> {
                    log.warn("DEMO_NOT_FOUND demoId={} producerId={}", demoId, producerId);
                    return new BusinessException(ShareErrorCode.DEMO_NOT_FOUND);
                });

        if (!demo.getOwnerId().equals(producerId)) {
            log.warn("FORBIDDEN_ACCESS demoId={} producerId={} ownerId={}",
                    demoId, producerId, demo.getOwnerId());
            throw new BusinessException(ShareErrorCode.FORBIDDEN_ACCESS);
        }

        if (demo.getStatus() != DemoStatus.ACTIVE) {
            log.warn("DEMO_NOT_ACTIVE demoId={} status={}", demoId, demo.getStatus());
            throw new BusinessException(ShareErrorCode.DEMO_NOT_ACTIVE);
        }

        return demo;
    }

    public Demo assertOwned(UUID demoId, UUID producerId) {
        Demo demo = demoRepository.findById(demoId)
                .orElseThrow(() -> {
                    log.warn("DEMO_NOT_FOUND demoId={} producerId={}", demoId, producerId);
                    return new BusinessException(ShareErrorCode.DEMO_NOT_FOUND);
                });

        if (!demo.getOwnerId().equals(producerId)) {
            log.warn("FORBIDDEN_ACCESS demoId={} producerId={} ownerId={}",
                    demoId, producerId, demo.getOwnerId());
            throw new BusinessException(ShareErrorCode.FORBIDDEN_ACCESS);
        }
        return demo;
    }
}