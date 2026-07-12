package com.pwb.backend.modules.share.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.common.security.HttpClientContextResolver;
import com.pwb.backend.modules.audio.cache.DemoStatusCache;
import com.pwb.backend.modules.audio.entity.Demo;
import com.pwb.backend.modules.audio.enums.DemoStatus;
import com.pwb.backend.modules.audio.repository.DemoRepository;
import com.pwb.backend.modules.audio.security.IpHashUtil;
import com.pwb.backend.modules.audio.security.StreamCookieSigner;
import com.pwb.backend.modules.iam.model.User;
import com.pwb.backend.modules.iam.repository.UserRepository;
import com.pwb.backend.modules.share.constant.ShareRedisKeys;
import com.pwb.backend.modules.share.dto.response.SharedThreadResponse;
import com.pwb.backend.modules.share.entity.DemoDistribution;
import com.pwb.backend.modules.share.entity.SharedThread;
import com.pwb.backend.modules.share.exception.ShareErrorCode;
import com.pwb.backend.modules.share.repository.DemoDistributionRepository;
import com.pwb.backend.modules.share.repository.SharedThreadRepository;
import com.pwb.backend.modules.share.service.SharedStreamService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SharedStreamServiceImpl implements SharedStreamService {

    private final DemoDistributionRepository demoDistributionRepository;
    private final SharedThreadRepository sharedThreadRepository;
    private final DemoRepository demoRepository;
    private final UserRepository userRepository;
    private final DemoStatusCache demoStatusCache;
    private final StreamCookieSigner cookieSigner;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final IpHashUtil ipHashUtil;
    private final HttpClientContextResolver clientContextResolver;

    @Override
    @Transactional(readOnly = true)
    public SharedThreadResponse loadSharedThread(UUID shareToken, HttpServletRequest request) {
        DemoDistribution distribution = demoDistributionRepository.findByShareToken(shareToken)
                .orElseThrow(() -> {
                    log.warn("LINK_NOT_FOUND shareToken={}", shareToken);
                    return new BusinessException(ShareErrorCode.LINK_NOT_FOUND);
                });

        if (distribution.isRevoked()) {
            String ipHash = ipHashUtil.hash(clientContextResolver.resolveIp(request));
            log.warn("REVOKED_LINK_ACCESS_ATTEMPT token={} demoId={} ipHash={}",
                    shareToken, distribution.getDemoId(), ipHash);
            throw new BusinessException(ShareErrorCode.LINK_REVOKED);
        }

        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(
                ShareRedisKeys.distributionRevokedKey(shareToken)))) {
            String ipHash = ipHashUtil.hash(clientContextResolver.resolveIp(request));
            log.warn("REVOKED_LINK_ACCESS_ATTEMPT token={} reason=redis_blacklist ipHash={}",
                    shareToken, ipHash);
            throw new BusinessException(ShareErrorCode.LINK_REVOKED);
        }

        DemoStatus status = resolveDemoStatus(distribution.getDemoId());
        if (status != DemoStatus.ACTIVE) {
            log.warn("DEMO_NOT_ACTIVE demoId={} status={}", distribution.getDemoId(), status);
            throw new BusinessException(ShareErrorCode.DEMO_NOT_ACTIVE);
        }

        Demo demo = demoRepository.findById(distribution.getDemoId())
                .orElseThrow(() -> new BusinessException(ShareErrorCode.DEMO_NOT_FOUND,
                        "Demo associated with distribution no longer exists"));

        SharedThread thread = sharedThreadRepository.findById(distribution.getThreadId())
                .orElseThrow(() -> new BusinessException(ShareErrorCode.LINK_NOT_FOUND,
                        "Shared thread missing for distribution"));

        User producer = userRepository.findById(thread.getProducerId())
                .orElseThrow(() -> new BusinessException(ShareErrorCode.LINK_NOT_FOUND,
                        "Producer not found for shared thread"));

        String producerDisplayName = producer.getFullName() != null && !producer.getFullName().isBlank()
                ? producer.getFullName()
                : producer.getUsername();

        Double duration = demo.getDuration() != null
                ? demo.getDuration().doubleValue()
                : null;

        float[] waveform = parseWaveform(demo.getWaveformData());

        String playlistUrl = "/api/v1/stream/" + shareToken + "/playlist.m3u8";

        log.info("SHARED_LINK_ACCESSED token={} demoId={} producerId={}",
                shareToken, demo.getId(), producer.getId());

        return new SharedThreadResponse(
                thread.getId(),
                distribution.getId(),
                distribution.getRecipientEmail(),
                producerDisplayName,
                distribution.isAllowDownload(),
                demo.getTitle(),
                duration,
                waveform,
                playlistUrl);
    }

    @Override
    public String issueSessionCookie(UUID shareToken, UUID demoId, HttpServletRequest request) {
        String clientIpSubnet = cookieSigner.resolveClientIpSubnet(request);
        StreamCookieSigner.IssuedCookie issued = cookieSigner.issue(shareToken, clientIpSubnet, demoId);
        log.info("SECURE_COOKIE_ISSUED token={} demoId={} jti={} ipSubnet={}",
                shareToken, demoId, issued.jti(), clientIpSubnet);
        return issued.token();
    }

    @Override
    public String cookieSubnetFor(HttpServletRequest request) {
        return cookieSigner.resolveClientIpSubnet(request);
    }

    private DemoStatus resolveDemoStatus(UUID demoId) {
        DemoStatus cached = demoStatusCache.get(demoId);
        if (cached != null) {
            return cached;
        }
        DemoStatus status = demoRepository.findById(demoId)
                .map(Demo::getStatus)
                .orElseThrow(() -> new BusinessException(ShareErrorCode.DEMO_NOT_FOUND,
                        "Demo not found for status check"));
        demoStatusCache.put(demoId, status);
        return status;
    }

    private float[] parseWaveform(String waveformJson) {
        if (waveformJson == null || waveformJson.isBlank()) {
            return new float[0];
        }
        try {
            Optional<Map<String, Object>> container = tryParseJsonObject(waveformJson);
            if (container.isPresent() && container.get().get("peaks") instanceof java.util.List<?> list) {
                float[] result = new float[list.size()];
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    if (item instanceof Number number) {
                        result[i] = number.floatValue();
                    }
                }
                return result;
            }
            float[] direct = objectMapper.readValue(waveformJson, float[].class);
            return direct != null ? direct : new float[0];
        } catch (JsonProcessingException ex) {
            log.warn("WAVEFORM_PARSE_FAILED reason={}", ex.getMessage());
            return new float[0];
        }
    }

    private Optional<Map<String, Object>> tryParseJsonObject(String waveformJson) {
        try {
            Map<String, Object> value = objectMapper.readValue(waveformJson,
                    new TypeReference<Map<String, Object>>() {});
            return Optional.ofNullable(value);
        } catch (JsonProcessingException ex) {
            return Optional.empty();
        }
    }
}
