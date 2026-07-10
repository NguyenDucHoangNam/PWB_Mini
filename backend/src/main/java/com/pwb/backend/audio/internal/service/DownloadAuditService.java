package com.pwb.backend.audio.internal.service;

import com.pwb.backend.audio.internal.helper.IpHashService;
import com.pwb.backend.audio.internal.model.DemoDownload;
import com.pwb.backend.audio.internal.repository.DemoDownloadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadAuditService {

  private final DemoDownloadRepository repository;
  private final IpHashService ipHashService;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(String distributionId, String demoId, String sessionIdHash,
                     String ip, String s3Key, long fileSizeBytes) {
    DemoDownload row = new DemoDownload();
    row.setDistributionId(distributionId);
    row.setDemoId(demoId);
    row.setSessionIdHash(sessionIdHash);
    row.setIpSubnetHash(ipHashService.hashSubnetV4(ip));
    row.setS3Key(s3Key);
    row.setFileSizeBytes(fileSizeBytes);
    repository.save(row);
    log.info("DOWNLOAD_AUDIT_RECORDED distributionId={} sessionId={} fileSizeBytes={}",
        distributionId, sessionIdHash, fileSizeBytes);
  }
}