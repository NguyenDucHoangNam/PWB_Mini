package com.pwb.backend.audio.internal.service;

import com.pwb.backend.audio.internal.api.DemoStatusResponse;
import com.pwb.backend.audio.internal.model.Demo;
import com.pwb.backend.audio.internal.repository.DemoRepository;
import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DemoQueryService {

  private final DemoRepository demoRepository;

  @Transactional(readOnly = true)
  public DemoStatusResponse getStatus(String demoId, String userId) {
    Demo demo = demoRepository.findByIdAndDeletedFalse(demoId)
        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
            "Demo not found"));
    if (!demo.getOwnerId().equals(userId)) {
      throw new BusinessException(ErrorCode.FORBIDDEN,
          "You do not have access to this demo");
    }
    return new DemoStatusResponse(
        demo.getId(),
        demo.getStatus().name(),
        demo.getErrorMessage(),
        demo.getWaveformData(),
        demo.getDuration(),
        demo.getSampleRate(),
        demo.getFormat(),
        demo.getHlsPlaylistS3Key()
    );
  }
}
