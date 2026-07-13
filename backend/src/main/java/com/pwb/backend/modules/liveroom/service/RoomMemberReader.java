package com.pwb.backend.modules.liveroom.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.modules.liveroom.constant.LiveRoomRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomMemberReader {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public boolean isMember(String roomCode, UUID userId) {
        if (roomCode == null || roomCode.isBlank() || userId == null) {
            return false;
        }
        try {
            Object raw = stringRedisTemplate.opsForHash()
                    .get(LiveRoomRedisKeys.roomMembersKey(roomCode), userId.toString());
            return raw != null;
        } catch (Exception ex) {
            log.warn("ROOM_MEMBER_LOOKUP_FAILED roomCode={} userId={} reason={}",
                    roomCode, userId, ex.getMessage());
            return false;
        }
    }

    public Optional<String> findDisplayName(String roomCode, UUID userId) {
        if (roomCode == null || roomCode.isBlank() || userId == null) {
            return Optional.empty();
        }
        try {
            Object raw = stringRedisTemplate.opsForHash()
                    .get(LiveRoomRedisKeys.roomMembersKey(roomCode), userId.toString());
            if (raw == null) {
                return Optional.empty();
            }
            String json = raw.toString();
            var node = objectMapper.readTree(json);
            var value = node.get("displayName");
            if (value == null || value.isNull()) {
                return Optional.empty();
            }
            String displayName = value.asText();
            if (displayName == null || displayName.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(displayName);
        } catch (Exception ex) {
            log.warn("ROOM_MEMBER_DISPLAYNAME_LOOKUP_FAILED roomCode={} userId={} reason={}",
                    roomCode, userId, ex.getMessage());
            return Optional.empty();
        }
    }
}
