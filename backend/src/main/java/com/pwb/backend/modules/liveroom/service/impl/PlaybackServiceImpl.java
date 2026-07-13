package com.pwb.backend.modules.liveroom.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.modules.audio.entity.Demo;
import com.pwb.backend.modules.audio.enums.DemoStatus;
import com.pwb.backend.modules.audio.exception.AudioErrorCode;
import com.pwb.backend.modules.audio.repository.DemoRepository;
import com.pwb.backend.modules.liveroom.config.LiveRoomProperties;
import com.pwb.backend.modules.liveroom.constant.LiveRoomRedisKeys;
import com.pwb.backend.modules.liveroom.dto.ws.PlaybackChangeMessage;
import com.pwb.backend.modules.liveroom.enums.RoomStatus;
import com.pwb.backend.modules.liveroom.exception.LiveRoomErrorCode;
import com.pwb.backend.modules.liveroom.service.LiveRoomMembershipNotifier;
import com.pwb.backend.modules.liveroom.service.PlaybackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaybackServiceImpl implements PlaybackService {

    private static final String FIELD_ACTIVE_SOURCE_ID = "activeSourceId";
    private static final String FIELD_PLAYBACK_STATE = "playbackState";
    private static final String FIELD_CURRENT_TIME = "currentTime";
    private static final String FIELD_SERVER_TIMESTAMP = "serverTimestamp";
    private static final String FIELD_LAST_UPDATED_BY = "lastUpdatedBy";
    private static final String PLAYBACK_STATE_PAUSED = "PAUSED";

    private static final String ROOM_HASH_FIELD_STATUS = "status";
    private static final String ROOM_HASH_FIELD_HOST_ID = "hostId";

    private final DemoRepository demoRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final LiveRoomMembershipNotifier notifier;
    private final LiveRoomProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public void selectSource(String roomCode, UUID hostId, UUID demoId) {
        log.info("SOURCE_SELECT_REQUEST roomCode={} demoId={} hostId={}", roomCode, demoId, hostId);

        Demo demo = demoRepository.findByIdAndOwnerId(demoId, hostId)
                .orElseThrow(() -> {
                    log.warn("SOURCE_CHANGE_FAILED_OWNERSHIP roomCode={} demoId={} hostId={}",
                            roomCode, demoId, hostId);
                    return new BusinessException(AudioErrorCode.DEMO_NOT_FOUND,
                            "Demo " + demoId + " not found or not owned by host",
                            null,
                            Map.<String, Object>of("demoId", demoId.toString()));
                });

        if (demo.getStatus() != DemoStatus.ACTIVE) {
            log.warn("SOURCE_CHANGE_FAILED_NOT_READY roomCode={} demoId={} status={}",
                    roomCode, demoId, demo.getStatus());
            throw new BusinessException(AudioErrorCode.DEMO_NOT_ACTIVE,
                    null,
                    null,
                    Map.<String, Object>of("demoId", demoId.toString()));
        }

        assertRoomHost(roomCode, hostId);

        long serverNow = Instant.now().toEpochMilli();
        double duration = demo.getDuration() != null ? demo.getDuration().doubleValue() : 0.0;
        List<Double> waveform = parseWaveform(demo.getWaveformData());

        stringRedisTemplate.execute(new SessionCallback<Object>() {
            @SuppressWarnings({"unchecked", "rawtypes"})
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                operations.multi();
                operations.opsForHash().put(LiveRoomRedisKeys.roomStatusKey(roomCode),
                        FIELD_ACTIVE_SOURCE_ID, demoId.toString());
                operations.opsForHash().put(LiveRoomRedisKeys.roomPlaybackKey(roomCode),
                        FIELD_PLAYBACK_STATE, PLAYBACK_STATE_PAUSED);
                operations.opsForHash().put(LiveRoomRedisKeys.roomPlaybackKey(roomCode),
                        FIELD_CURRENT_TIME, "0.0");
                operations.opsForHash().put(LiveRoomRedisKeys.roomPlaybackKey(roomCode),
                        FIELD_SERVER_TIMESTAMP, String.valueOf(serverNow));
                operations.opsForHash().put(LiveRoomRedisKeys.roomPlaybackKey(roomCode),
                        FIELD_LAST_UPDATED_BY, hostId.toString());
                operations.expire(LiveRoomRedisKeys.roomPlaybackKey(roomCode),
                        Duration.ofSeconds(properties.getPhase2TtlSeconds()));
                return operations.exec();
            }
        });

        PlaybackChangeMessage.Data data = new PlaybackChangeMessage.Data(
                demoId, demo.getTitle(), duration, waveform, hostId, serverNow);
        PlaybackChangeMessage payload = new PlaybackChangeMessage("SOURCE_CHANGED", data);
        notifier.notifyPlaybackChange(roomCode, payload);

        log.info("SOURCE_CHANGED_SUCCESS roomCode={} demoId={} duration={}", roomCode, demoId, duration);
    }

    private void assertRoomHost(String roomCode, UUID hostId) {
        Map<Object, Object> statusHash = stringRedisTemplate.opsForHash()
                .entries(LiveRoomRedisKeys.roomStatusKey(roomCode));
        if (statusHash.isEmpty()) {
            throw new BusinessException(LiveRoomErrorCode.ROOM_NOT_FOUND,
                    null,
                    null,
                    Map.<String, Object>of("roomCode", roomCode));
        }
        String status = asString(statusHash.get(ROOM_HASH_FIELD_STATUS));
        String owner = asString(statusHash.get(ROOM_HASH_FIELD_HOST_ID));
        if (!RoomStatus.ACTIVE.name().equals(status) && !RoomStatus.INACTIVE_HOST.name().equals(status)) {
            throw new BusinessException(LiveRoomErrorCode.ROOM_NOT_FOUND,
                    null,
                    null,
                    Map.<String, Object>of("roomCode", roomCode));
        }
        if (owner == null || !owner.equals(hostId.toString())) {
            log.warn("HOST_FORBIDDEN roomCode={} callerId={} ownerId={}", roomCode, hostId, owner);
            throw new BusinessException(LiveRoomErrorCode.FORBIDDEN_NOT_HOST,
                    null,
                    null,
                    Map.<String, Object>of("roomCode", roomCode));
        }
    }

    private List<Double> parseWaveform(String waveformJson) {
        if (waveformJson == null || waveformJson.isBlank()) {
            return List.of();
        }
        try {
            var node = objectMapper.readTree(waveformJson);
            if (!node.isArray()) {
                return List.of();
            }
            List<Double> peaks = new ArrayList<>(node.size());
            for (var item : node) {
                peaks.add(item.asDouble());
            }
            return peaks;
        } catch (Exception ex) {
            log.warn("WAVEFORM_PARSE_FAILED reason={}", ex.getMessage());
            return List.of();
        }
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
