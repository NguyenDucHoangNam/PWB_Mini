package com.pwb.liveroom.application.event;

import com.pwb.liveroom.application.command.RtcIceCandidate;
import com.pwb.liveroom.application.command.RtcSignalType;
import com.pwb.liveroom.application.view.TrackCommentView;
import com.pwb.liveroom.domain.enums.EndedReason;
import com.pwb.liveroom.domain.model.ChatMessage;
import com.pwb.liveroom.domain.model.JoinRequest;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.domain.model.Participant;
import com.pwb.liveroom.domain.model.PlaybackState;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


public final class RoomEvents {

    private RoomEvents() {
    }

    public static RoomEvent participantJoined(LiveRoom room, Participant participant, String avatarUrl) {
        return event(LiveroomEventType.PARTICIPANT_JOINED, room.getId(), map(
                "userId", participant.getUserId(),
                "userEmail", participant.getUserEmail(),
                "avatarUrl", avatarUrl,
                "roomRole", participant.getRoomRole(),
                "joinedAt", participant.getJoinedAt()
        ));
    }

    public static RoomEvent participantLeft(LiveRoom room, Participant participant) {
        return event(LiveroomEventType.PARTICIPANT_LEFT, room.getId(), map(
                "userId", participant.getUserId(),
                "userEmail", participant.getUserEmail(),
                "leftAt", participant.getLeftAt()
        ));
    }

    public static RoomEvent participantKicked(LiveRoom room, Participant participant, UUID kickedBy,
                                              String reason, Instant cooldownUntil) {
        return event(LiveroomEventType.PARTICIPANT_KICKED, room.getId(), map(
                "userId", participant.getUserId(),
                "userEmail", participant.getUserEmail(),
                "kickedBy", kickedBy,
                "reason", reason,
                "cooldownUntil", cooldownUntil
        ));
    }

    public static RoomEvent mediaStateChanged(LiveRoom room, Participant participant) {
        return event(LiveroomEventType.MEDIA_STATE_CHANGED, room.getId(), map(
                "userId", participant.getUserId(),
                "cameraOn", participant.isCameraOn(),
                "micOn", participant.isMicOn(),
                "micState", participant.getMicState()
        ));
    }


    public static RoomEvent micMutedByOwner(LiveRoom room, Participant participant, UUID mutedBy) {
        return event(LiveroomEventType.PARTICIPANT_MIC_MUTED_BY_OWNER, room.getId(), map(
                "userId", participant.getUserId(),
                "mutedBy", mutedBy,
                "mutedAt", participant.getMicMutedByOwnerAt(),
                "cooldownUntil", participant.getMicUnmuteCooldownUntil()
        ));
    }

    public static RoomEvent micUnmuted(LiveRoom room, Participant participant, Instant at) {
        return event(LiveroomEventType.PARTICIPANT_MIC_UNMUTED, room.getId(), map(
                "userId", participant.getUserId(),
                "unmutedAt", at
        ));
    }


    public static RoomEvent ownerLeft(LiveRoom room) {
        return event(LiveroomEventType.OWNER_LEFT, room.getId(), map(
                "ownerId", room.getOwnerId(),
                "ownerLeftAt", room.getOwnerLeftAt(),
                "graceExpiresAt", room.ownerGraceExpiresAt()
        ));
    }

    public static RoomEvent ownerRejoined(LiveRoom room, Instant at) {
        return event(LiveroomEventType.OWNER_REJOINED, room.getId(), map(
                "ownerId", room.getOwnerId(),
                "rejoinedAt", at
        ));
    }

    public static RoomEvent capacityChanged(LiveRoom room) {
        return event(LiveroomEventType.ROOM_CAPACITY_CHANGED, room.getId(), map(
                "currentCount", room.getCurrentParticipantCount(),
                "maxParticipants", room.getMaxParticipants(),
                "effectiveMaxParticipants", room.effectiveMaxParticipants(),
                "reservedOwnerSlot", room.isReservedOwnerSlot()
        ));
    }

    public static RoomEvent capacityReached(LiveRoom room) {
        return event(LiveroomEventType.CAPACITY_REACHED, room.getId(), map(
                "currentCount", room.getCurrentParticipantCount(),
                "effectiveMaxParticipants", room.effectiveMaxParticipants()
        ));
    }

    public static RoomEvent joinRequestCreated(JoinRequest request, String avatarUrl) {
        return event(LiveroomEventType.JOIN_REQUEST_CREATED, request.getRoomId(), map(
                "requestId", request.getId(),
                "userId", request.getUserId(),
                "userEmail", request.getUserEmail(),
                "avatarUrl", avatarUrl,
                "createdAt", request.getCreatedAt()
        ));
    }

    public static RoomEvent joinRequestCancelled(JoinRequest request) {
        return event(LiveroomEventType.JOIN_REQUEST_CANCELLED, request.getRoomId(), map(
                "requestId", request.getId(),
                "userId", request.getUserId(),
                "cancelledAt", request.getDecidedAt()
        ));
    }

    public static RoomEvent requestApproved(JoinRequest request) {
        return event(LiveroomEventType.REQUEST_APPROVED, request.getRoomId(), map(
                "requestId", request.getId(),
                "userId", request.getUserId(),
                "autoJoin", true
        ));
    }

    public static RoomEvent requestRejectedByOwner(JoinRequest request, int rejectCountByOwner, int attemptsRemaining) {
        return event(LiveroomEventType.REQUEST_REJECTED_BY_OWNER, request.getRoomId(), map(
                "requestId", request.getId(),
                "userId", request.getUserId(),
                "rejectCountByOwner", rejectCountByOwner,
                "attemptsRemaining", attemptsRemaining
        ));
    }

    public static RoomEvent requestRejectedByCapacity(JoinRequest request, int rejectCountByCapacity) {
        return event(LiveroomEventType.REQUEST_REJECTED_BY_CAPACITY, request.getRoomId(), map(
                "requestId", request.getId(),
                "userId", request.getUserId(),
                "rejectCountByCapacity", rejectCountByCapacity
        ));
    }

    public static RoomEvent rtcDescription(UUID roomId, UUID fromUserId, RtcSignalType type, String sdp) {
        return event(type.eventType(), roomId, map(
                "fromUserId", fromUserId,
                "sdp", sdp
        ));
    }

    public static RoomEvent rtcIceCandidate(UUID roomId, UUID fromUserId, RtcIceCandidate candidate) {
        return event(LiveroomEventType.RTC_ICE_CANDIDATE, roomId, map(
                "fromUserId", fromUserId,
                "candidate", candidate.candidate(),
                "sdpMid", candidate.sdpMid(),
                "sdpMLineIndex", candidate.sdpMLineIndex(),
                "usernameFragment", candidate.usernameFragment()
        ));
    }

    public static RoomEvent requestLocked(UUID roomId, UUID userId, int rejectCountByOwner) {
        return event(LiveroomEventType.REQUEST_LOCKED, roomId, map(
                "userId", userId,
                "rejectCountByOwner", rejectCountByOwner
        ));
    }


    public static RoomEvent chatMessageReceived(LiveRoom room, ChatMessage message) {
        return event(LiveroomEventType.CHAT_MESSAGE_RECEIVED, room.getId(), map(
                "messageId", message.getId(),
                "cycleId", message.getCycleId(),
                "userId", message.getUserId(),
                "userEmail", message.getUserEmail(),
                "content", message.getContent(),
                "sentAt", message.getSentAt()
        ));
    }


    public static RoomEvent musicState(LiveRoom room, PlaybackState state, boolean songChanged, Instant now) {
        LiveroomEventType type = songChanged
                ? LiveroomEventType.MUSIC_SONG_CHANGED
                : LiveroomEventType.MUSIC_PLAYBACK_STATE_CHANGED;
        return event(type, room.getId(), map(
                "songId", state.getSongId(),
                "songOwnerId", state.getSongOwnerId(),
                "songTitle", state.getSongTitle(),
                "songArtist", state.getSongArtist(),
                "songDurationSeconds", state.getSongDurationSeconds(),
                "status", state.getStatus(),
                "positionSeconds", state.positionAt(now),
                "volumePercent", state.getVolumePercent(),
                "startedAt", state.getStartedAt(),
                "lastUpdatedAt", state.getLastUpdatedAt(),
                "lastUpdatedBy", state.getLastUpdatedBy(),
                "sequenceNumber", state.getSequenceNumber(),
                "ownerAbsent", room.isOwnerAbsent()
        ));
    }

    public static RoomEvent trackCommentAdded(LiveRoom room, TrackCommentView comment) {
        return event(LiveroomEventType.TRACK_COMMENT_ADDED, room.getId(), map(
                "commentId", comment.id(),
                "songId", comment.songId(),
                "userId", comment.userId(),
                "userEmail", comment.userEmail(),
                "content", comment.content(),
                "positionSeconds", comment.positionSeconds(),
                "createdAt", comment.createdAt()
        ));
    }

    public static RoomEvent trackCommentSnapshot(LiveRoom room, UUID songId, List<TrackCommentView> comments) {
        return event(LiveroomEventType.TRACK_COMMENT_SNAPSHOT, room.getId(), map(
                "songId", songId,
                "comments", comments
        ));
    }

    public static RoomEvent roomManuallyEnded(LiveRoom room) {
        return event(LiveroomEventType.ROOM_MANUAL_ENDED, room.getId(), map(
                "endedAt", room.getEndedAt(),
                "reason", "manual"
        ));
    }


    public static RoomEvent roomAutoEnded(LiveRoom room, EndedReason reason) {
        return event(LiveroomEventType.ROOM_AUTO_ENDED, room.getId(), map(
                "endedAt", room.getEndedAt(),
                "reason", reason
        ));
    }

    public static RoomEvent roomRevived(LiveRoom room, Instant at, Instant revokedEndedAt) {
        return event(LiveroomEventType.ROOM_REVIVED, room.getId(), map(
                "revivedAt", at,
                "revokedEndedAt", revokedEndedAt
        ));
    }

    public static RoomEvent roomReopened(LiveRoom room) {
        return event(LiveroomEventType.ROOM_REOPENED, room.getId(), map(
                "reopenedCount", room.getReopenedCount(),
                "previousEndedAt", room.getPreviousEndedAt(),
                "startedAt", room.getLastReopenedAt()
        ));
    }

    private static RoomEvent event(LiveroomEventType type, UUID roomId, Map<String, Object> data) {
        return new RoomEvent(type, roomId, Instant.now(), data);
    }


    private static Map<String, Object> map(Object... keyValuePairs) {
        Map<String, Object> data = new HashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            data.put((String) keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return data;
    }
}