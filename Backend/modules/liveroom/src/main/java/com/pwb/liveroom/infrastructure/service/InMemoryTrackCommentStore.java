package com.pwb.liveroom.infrastructure.service;

import com.pwb.liveroom.domain.model.TrackComment;
import com.pwb.liveroom.domain.service.TrackCommentStore;
import com.pwb.liveroom.infrastructure.config.properties.LiveroomConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
@Service
@RequiredArgsConstructor
public class InMemoryTrackCommentStore implements TrackCommentStore {

    private static final Comparator<TrackComment> BY_POSITION =
            Comparator.comparingDouble(TrackComment::getPositionSeconds)
                    .thenComparing(TrackComment::getCreatedAt);

    private final LiveroomConfig config;

    private final Map<UUID, Map<UUID, List<TrackComment>>> byCycle = new ConcurrentHashMap<>();

    @Override
    public TrackComment add(TrackComment comment) {
        List<TrackComment> bucket = byCycle
                .computeIfAbsent(comment.getCycleId(), key -> new ConcurrentHashMap<>())
                .computeIfAbsent(comment.getSongId(), key -> new ArrayList<>());

        synchronized (bucket) {
            bucket.add(comment);
            int limit = config.getComments().getPerSong();
            while (bucket.size() > limit) {
                bucket.remove(0);
            }
        }
        return comment;
    }

    @Override
    public List<TrackComment> findBySong(UUID cycleId, UUID songId) {
        Map<UUID, List<TrackComment>> bySong = byCycle.get(cycleId);
        if (bySong == null) {
            return List.of();
        }
        List<TrackComment> bucket = bySong.get(songId);
        if (bucket == null) {
            return List.of();
        }
        synchronized (bucket) {
            return bucket.stream().sorted(BY_POSITION).toList();
        }
    }

    @Override
    public void clearCycle(UUID cycleId) {
        if (cycleId == null) {
            return;
        }
        Map<UUID, List<TrackComment>> dropped = byCycle.remove(cycleId);
        if (dropped != null) {
            log.debug("Dropped track comments for cycle: cycleId={} songs={}", cycleId, dropped.size());
        }
    }
}