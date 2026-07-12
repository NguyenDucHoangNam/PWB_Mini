package com.pwb.backend.modules.audio.service.impl;

import com.pwb.backend.modules.audio.config.AudioProperties;
import com.pwb.backend.modules.audio.entity.Demo;
import com.pwb.backend.modules.audio.repository.DemoRepository;
import com.pwb.backend.modules.audio.service.PlaylistService;
import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.modules.share.entity.DemoDistribution;
import com.pwb.backend.modules.share.exception.ShareErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaylistServiceImpl implements PlaylistService {

    private static final BigDecimal MIN_SEGMENT_SECONDS = new BigDecimal("0.01");

    private final DemoRepository demoRepository;
    private final AudioProperties audioProperties;

    @Override
    public String buildM3U8(UUID shareToken, DemoDistribution distribution) {
        Demo demo = demoRepository.findById(distribution.getDemoId())
                .orElseThrow(() -> new BusinessException(ShareErrorCode.DEMO_NOT_FOUND,
                        "Demo not found for playlist build"));

        BigDecimal segmentDuration = BigDecimal.valueOf(audioProperties.getHlsSegmentSeconds());
        if (segmentDuration.compareTo(MIN_SEGMENT_SECONDS) <= 0) {
            segmentDuration = BigDecimal.valueOf(6);
        }

        BigDecimal durationSeconds = demo.getDuration() != null && demo.getDuration().signum() > 0
                ? demo.getDuration()
                : BigDecimal.valueOf(60);

        int segmentCount = durationSeconds
                .divide(segmentDuration, 0, java.math.RoundingMode.CEILING)
                .intValue();
        if (segmentCount < 1) {
            segmentCount = 1;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("#EXTM3U\n");
        sb.append("#EXT-X-VERSION:3\n");
        sb.append("#EXT-X-PLAYLIST-TYPE:VOD\n");
        sb.append("#EXT-X-TARGETDURATION:").append(segmentDuration.toPlainString()).append("\n");
        sb.append("#EXT-X-MEDIA-SEQUENCE:0\n");
        sb.append("#EXT-X-KEY:METHOD=AES-128,URI=\"/api/v1/stream/keys/").append(shareToken).append("\",IV=0x00000000000000000000000000000000\n");

        for (int i = 0; i < segmentCount; i++) {
            sb.append("#EXTINF:").append(segmentDuration.toPlainString()).append(",\n");
            sb.append("/stream/").append(demo.getId()).append("/seq_").append(String.format("%03d", i)).append(".ts\n");
        }
        sb.append("#EXT-X-ENDLIST\n");

        log.info("PLAYLIST_GENERATED token={} demoId={} segments={}",
                shareToken, demo.getId(), segmentCount);
        return sb.toString();
    }
}
