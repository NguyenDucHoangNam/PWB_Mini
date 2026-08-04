package com.pwb.liveroom.infrastructure.persistence.adapter;

import com.pwb.liveroom.application.exception.LiveroomBusinessException;
import com.pwb.liveroom.application.exception.LiveroomErrorCode;
import com.pwb.liveroom.domain.model.PlaybackState;
import com.pwb.liveroom.domain.repository.PlaybackStateRepository;
import com.pwb.liveroom.infrastructure.persistence.entity.PlaybackStateJpaEntity;
import com.pwb.liveroom.infrastructure.persistence.mapper.PlaybackStateMapper;
import com.pwb.liveroom.infrastructure.persistence.repository.PlaybackStateJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PlaybackStateRepositoryImpl implements PlaybackStateRepository {

    private final PlaybackStateJpaRepository playbackStateJpaRepository;
    private final PlaybackStateMapper playbackStateMapper;

    @Override
    public Optional<PlaybackState> findByRoomId(UUID roomId) {
        return playbackStateJpaRepository.findByRoomId(roomId)
                .map(playbackStateMapper::toDomain);
    }


    @Override
    @Transactional
    public PlaybackState save(PlaybackState state) {
        try {
            return playbackStateMapper.toDomain(
                    playbackStateJpaRepository.saveAndFlush(toManaged(state)));
        } catch (DataIntegrityViolationException ex) {
            throw new LiveroomBusinessException(LiveroomErrorCode.MUSIC_STATE_CONFLICT, ex);
        }
    }

    @Override
    @Transactional
    public void deleteByRoomId(UUID roomId) {
        playbackStateJpaRepository.deleteByRoomId(roomId);
    }

    private PlaybackStateJpaEntity toManaged(PlaybackState state) {
        if (state.isNew()) {
            return playbackStateMapper.toEntity(state);
        }
        PlaybackStateJpaEntity target = playbackStateJpaRepository.findById(state.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Playback state no longer exists: " + state.getId()));
        playbackStateMapper.applyTo(state, target);
        return target;
    }
}