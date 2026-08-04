package com.pwb.liveroom.infrastructure.persistence.adapter;

import com.pwb.liveroom.domain.model.RoomMember;
import com.pwb.liveroom.domain.repository.RoomMemberRepository;
import com.pwb.liveroom.infrastructure.persistence.entity.RoomMemberJpaEntity;
import com.pwb.liveroom.infrastructure.persistence.mapper.RoomMemberMapper;
import com.pwb.liveroom.infrastructure.persistence.repository.RoomMemberJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RoomMemberRepositoryImpl implements RoomMemberRepository {

    private final RoomMemberJpaRepository memberJpaRepository;
    private final RoomMemberMapper memberMapper;

    @Override
    public RoomMember save(RoomMember member) {
        RoomMemberJpaEntity entity;
        if (member.isNew()) {
            entity = memberMapper.toEntity(member);
        } else {
            entity = memberJpaRepository.findById(member.getId())
                    .orElseThrow(() -> new IllegalStateException("Room member no longer exists: " + member.getId()));
            memberMapper.applyTo(member, entity);
        }
        return memberMapper.toDomain(memberJpaRepository.save(entity));
    }

    @Override
    public Optional<RoomMember> findByRoomIdAndUserId(UUID roomId, UUID userId) {
        return memberJpaRepository.findByRoomIdAndUserId(roomId, userId)
                .map(memberMapper::toDomain);
    }

    @Override
    public void resetRejectCountersForRoom(UUID roomId) {
        memberJpaRepository.resetRejectCountersForRoom(roomId);
    }
}