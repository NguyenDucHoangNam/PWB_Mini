package com.pwb.liveroom.application.support;

import com.pwb.liveroom.application.event.LiveroomEventPublisher;
import com.pwb.liveroom.application.event.RoomEvents;
import com.pwb.liveroom.application.exception.LiveroomBusinessException;
import com.pwb.liveroom.application.exception.LiveroomErrorCode;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.domain.model.Participant;
import com.pwb.liveroom.domain.model.PlaybackState;
import com.pwb.liveroom.domain.repository.PlaybackStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;


@Slf4j
@Component
@RequiredArgsConstructor
public class Playbacks {

    private final RoomSessions roomSessions;
    private final PlaybackStateRepository playbackStateRepository;
    private final LiveroomEventPublisher eventPublisher;


    public LiveRoom requireActiveRoom(UUID roomId) {
        return roomSessions.requireActiveRoom(roomId);
    }


    public Participant requireInRoom(LiveRoom room, UUID actorId) {
        return roomSessions.requireInRoom(room, actorId);
    }


    public boolean canStartAudio(LiveRoom room, UUID actorId) {
        return !room.isOwnerAbsent() || room.isOwnedBy(actorId);
    }

    public void requireCanStartAudio(LiveRoom room, UUID actorId) {
        if (!canStartAudio(room, actorId)) {
            throw new LiveroomBusinessException(LiveroomErrorCode.MUSIC_OWNER_ABSENT);
        }
    }


    public PlaybackState loadOrSilent(UUID roomId, Instant at) {
        return playbackStateRepository.findByRoomId(roomId)
                .orElseGet(() -> PlaybackState.silent(roomId, at));
    }


    public PlaybackState requireWithSong(UUID roomId) {
        return playbackStateRepository.findByRoomId(roomId)
                .filter(PlaybackState::hasSong)
                .orElseThrow(() -> new LiveroomBusinessException(LiveroomErrorCode.MUSIC_NOT_PLAYING));
    }

    public PlaybackState save(PlaybackState state) {
        return playbackStateRepository.save(state);
    }

    public void announce(LiveRoom room, PlaybackState state, boolean songChanged, Instant now) {
        eventPublisher.broadcastToRoomChannel(
                RoomEvents.musicState(room, state, songChanged, now),
                LiveroomEventPublisher.MUSIC_CHANNEL);
    }


    public void sendCurrentTo(UUID userId, LiveRoom room, Instant now) {
        playbackStateRepository.findByRoomId(room.getId()).ifPresent(state ->
                eventPublisher.sendToUser(userId, RoomEvents.musicState(room, state, false, now)));
    }


    public void pauseForOwnerAbsence(LiveRoom room, Instant at) {
        pauseIfPlaying(room, at).ifPresent(paused -> {
            announce(room, paused, false, at);
            log.debug("Music paused because the owner left: roomId={}", room.getId());
        });
    }


    public void freezeOnRoomEnd(LiveRoom room, Instant at) {
        pauseIfPlaying(room, at);
    }


    public void clear(UUID roomId) {
        playbackStateRepository.deleteByRoomId(roomId);
    }

    private java.util.Optional<PlaybackState> pauseIfPlaying(LiveRoom room, Instant at) {
        return playbackStateRepository.findByRoomId(room.getId())
                .filter(state -> state.hasSong() && state.isPlaying())
                .map(state -> {


                    state.pause(room.getOwnerId(), at);
                    return playbackStateRepository.save(state);
                });
    }
}