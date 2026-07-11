package com.pwb.backend.audio.internal.application.service;

import com.pwb.backend.audio.internal.application.helper.AesKeyManager;
import com.pwb.backend.audio.internal.domain.model.Demo;
import com.pwb.backend.audio.internal.infrastructure.repository.DemoRepository;
import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.audio.internal.domain.exception.AudioErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AesKeyCacheService {

  private final AesKeyManager aesKeyManager;
  private final DemoRepository demoRepository;

  public byte[] getActiveKey(String demoId) {
    Optional<Demo> opt = demoRepository.findById(demoId);
    if (opt.isEmpty()) {
      throw new BusinessException(AudioErrorCode.DEMO_NOT_FOUND, "Demo not found");
    }
    Demo demo = opt.get();
    if (demo.getAesKeyEncrypted() == null) {
      throw new BusinessException(AudioErrorCode.AES_KEY_GENERATION_FAILED, "AES key not generated for demo");
    }
    int version = demo.getAesKeyVersion();
    return aesKeyManager.getFromCache(demoId, version)
        .orElseGet(() -> {
          byte[] decrypted = aesKeyManager.decryptMaster(demo.getAesKeyEncrypted());
          aesKeyManager.cacheInRedis(demoId, decrypted, version);
          return decrypted;
        });
  }

  public void evict(String demoId) {
    aesKeyManager.evict(demoId);
    log.info("AES_KEY_CACHE_EVICTED demoId={} trigger=manual", demoId);
  }

  public void evictForRevoke(String demoId) {
    evict(demoId);
    log.info("AES_KEY_CACHE_EVICTED demoId={} trigger=revoke", demoId);
  }
}