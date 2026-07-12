package com.pwb.backend.modules.liveroom.dto.ws;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MembersSnapshotMessage(
        String roomCode,
        List<MemberEntry> members,
        int activeCount,
        int maxParticipants,
        Instant snapshotAt) {

    public record MemberEntry(
            UUID userId,
            String displayName,
            String role,
            Instant joinedAt) {
    }
}