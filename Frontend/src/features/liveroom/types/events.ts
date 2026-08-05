import type {
  EndedReason,
  MicState,
  ParticipantRole,
  PlaybackStatus,
  TrackComment,
} from "./index";

export type LiveroomEventType =
  | "PARTICIPANT_JOINED"
  | "PARTICIPANT_LEFT"
  | "PARTICIPANT_KICKED"
  | "MEDIA_STATE_CHANGED"
  | "PARTICIPANT_MIC_MUTED_BY_OWNER"
  | "PARTICIPANT_MIC_UNMUTED"
  | "OWNER_LEFT"
  | "OWNER_REJOINED"
  | "ROOM_CAPACITY_CHANGED"
  | "CAPACITY_REACHED"
  | "JOIN_REQUEST_CREATED"
  | "JOIN_REQUEST_CANCELLED"
  | "REQUEST_APPROVED"
  | "REQUEST_REJECTED_BY_OWNER"
  | "REQUEST_REJECTED_BY_CAPACITY"
  | "REQUEST_LOCKED"
  | "CHAT_MESSAGE_RECEIVED"
  | "MUSIC_PLAYBACK_STATE_CHANGED"
  | "MUSIC_SONG_CHANGED"
  | "TRACK_COMMENT_ADDED"
  | "TRACK_COMMENT_SNAPSHOT"
  | "RTC_OFFER"
  | "RTC_ANSWER"
  | "RTC_ICE_CANDIDATE"
  | "ROOM_MANUAL_ENDED"
  | "ROOM_AUTO_ENDED"
  | "ROOM_REVIVED"
  | "ROOM_REOPENED";

interface Envelope<T extends LiveroomEventType, D> {
  type: T;
  roomId: string;
  timestamp: string;
  data: D;
}

export interface MusicStateData {
  songId: string | null;
  songOwnerId: string | null;
  songTitle: string | null;
  songArtist: string | null;
  songDurationSeconds: number | null;
  status: PlaybackStatus;
  positionSeconds: number;
  volumePercent: number;
  startedAt: string | null;
  lastUpdatedAt: string | null;
  lastUpdatedBy: string | null;
  sequenceNumber: number;
  ownerAbsent: boolean;
}

export type ParticipantJoinedEvent = Envelope<
  "PARTICIPANT_JOINED",
  {
    userId: string;
    userEmail: string;
    avatarUrl: string | null;
    roomRole: ParticipantRole;
    joinedAt: string;
  }
>;

export type ParticipantLeftEvent = Envelope<
  "PARTICIPANT_LEFT",
  { userId: string; userEmail: string; leftAt: string }
>;

export type ParticipantKickedEvent = Envelope<
  "PARTICIPANT_KICKED",
  {
    userId: string;
    userEmail: string;
    kickedBy: string;
    reason: string | null;
    cooldownUntil: string | null;
  }
>;

export type MediaStateChangedEvent = Envelope<
  "MEDIA_STATE_CHANGED",
  { userId: string; cameraOn: boolean; micOn: boolean; micState: MicState }
>;

export type MicMutedByOwnerEvent = Envelope<
  "PARTICIPANT_MIC_MUTED_BY_OWNER",
  { userId: string; mutedBy: string; mutedAt: string; cooldownUntil: string | null }
>;

export type MicUnmutedEvent = Envelope<
  "PARTICIPANT_MIC_UNMUTED",
  { userId: string; unmutedAt: string }
>;

export type OwnerLeftEvent = Envelope<
  "OWNER_LEFT",
  { ownerId: string; ownerLeftAt: string; graceExpiresAt: string | null }
>;

export type OwnerRejoinedEvent = Envelope<
  "OWNER_REJOINED",
  { ownerId: string; rejoinedAt: string }
>;

export type RoomCapacityChangedEvent = Envelope<
  "ROOM_CAPACITY_CHANGED",
  {
    currentCount: number;
    maxParticipants: number;
    effectiveMaxParticipants: number;
    reservedOwnerSlot: boolean;
  }
>;

export type CapacityReachedEvent = Envelope<
  "CAPACITY_REACHED",
  { currentCount: number; effectiveMaxParticipants: number }
>;

export type JoinRequestCreatedEvent = Envelope<
  "JOIN_REQUEST_CREATED",
  {
    requestId: string;
    userId: string;
    userEmail: string;
    avatarUrl: string | null;
    createdAt: string;
  }
>;

export type JoinRequestCancelledEvent = Envelope<
  "JOIN_REQUEST_CANCELLED",
  { requestId: string; userId: string; cancelledAt: string | null }
>;

export type RequestApprovedEvent = Envelope<
  "REQUEST_APPROVED",
  { requestId: string; userId: string; autoJoin: boolean }
>;

export type RequestRejectedByOwnerEvent = Envelope<
  "REQUEST_REJECTED_BY_OWNER",
  {
    requestId: string;
    userId: string;
    rejectCountByOwner: number;
    attemptsRemaining: number;
  }
>;

export type RequestRejectedByCapacityEvent = Envelope<
  "REQUEST_REJECTED_BY_CAPACITY",
  { requestId: string; userId: string; rejectCountByCapacity: number }
>;

export type RequestLockedEvent = Envelope<
  "REQUEST_LOCKED",
  { userId: string; rejectCountByOwner: number }
>;

export type ChatMessageReceivedEvent = Envelope<
  "CHAT_MESSAGE_RECEIVED",
  {
    messageId: string;
    cycleId: string;
    userId: string;
    userEmail: string;
    content: string;
    sentAt: string;
  }
>;

export type MusicPlaybackStateChangedEvent = Envelope<
  "MUSIC_PLAYBACK_STATE_CHANGED",
  MusicStateData
>;

export type MusicSongChangedEvent = Envelope<"MUSIC_SONG_CHANGED", MusicStateData>;

export type TrackCommentAddedEvent = Envelope<
  "TRACK_COMMENT_ADDED",
  {
    commentId: string;
    songId: string;
    userId: string;
    userEmail: string;
    content: string;
    positionSeconds: number;
    createdAt: string;
  }
>;

export type TrackCommentSnapshotEvent = Envelope<
  "TRACK_COMMENT_SNAPSHOT",
  { songId: string | null; comments: TrackComment[] }
>;

export type RtcOfferEvent = Envelope<"RTC_OFFER", { fromUserId: string; sdp: string }>;

export type RtcAnswerEvent = Envelope<"RTC_ANSWER", { fromUserId: string; sdp: string }>;

export type RtcIceCandidateEvent = Envelope<
  "RTC_ICE_CANDIDATE",
  {
    fromUserId: string;
    candidate: string;
    sdpMid: string | null;
    sdpMLineIndex: number | null;
    usernameFragment: string | null;
  }
>;

export type RoomManualEndedEvent = Envelope<
  "ROOM_MANUAL_ENDED",
  { endedAt: string; reason: "manual" }
>;

export type RoomAutoEndedEvent = Envelope<
  "ROOM_AUTO_ENDED",
  { endedAt: string; reason: EndedReason }
>;

export type RoomRevivedEvent = Envelope<
  "ROOM_REVIVED",
  { revivedAt: string; revokedEndedAt: string | null }
>;

export type RoomReopenedEvent = Envelope<
  "ROOM_REOPENED",
  { reopenedCount: number; previousEndedAt: string | null; startedAt: string | null }
>;

export type LiveroomEvent =
  | ParticipantJoinedEvent
  | ParticipantLeftEvent
  | ParticipantKickedEvent
  | MediaStateChangedEvent
  | MicMutedByOwnerEvent
  | MicUnmutedEvent
  | OwnerLeftEvent
  | OwnerRejoinedEvent
  | RoomCapacityChangedEvent
  | CapacityReachedEvent
  | JoinRequestCreatedEvent
  | JoinRequestCancelledEvent
  | RequestApprovedEvent
  | RequestRejectedByOwnerEvent
  | RequestRejectedByCapacityEvent
  | RequestLockedEvent
  | ChatMessageReceivedEvent
  | MusicPlaybackStateChangedEvent
  | MusicSongChangedEvent
  | TrackCommentAddedEvent
  | TrackCommentSnapshotEvent
  | RtcOfferEvent
  | RtcAnswerEvent
  | RtcIceCandidateEvent
  | RoomManualEndedEvent
  | RoomAutoEndedEvent
  | RoomRevivedEvent
  | RoomReopenedEvent;

export type LiveroomEventOf<T extends LiveroomEventType> = Extract<
  LiveroomEvent,
  { type: T }
>;

export function isMusicEvent(
  event: LiveroomEvent,
): event is MusicPlaybackStateChangedEvent | MusicSongChangedEvent {
  return (
    event.type === "MUSIC_PLAYBACK_STATE_CHANGED" || event.type === "MUSIC_SONG_CHANGED"
  );
}

export function isRtcEvent(
  event: LiveroomEvent,
): event is RtcOfferEvent | RtcAnswerEvent | RtcIceCandidateEvent {
  return (
    event.type === "RTC_OFFER" ||
    event.type === "RTC_ANSWER" ||
    event.type === "RTC_ICE_CANDIDATE"
  );
}