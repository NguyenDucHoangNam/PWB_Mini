export type LiveRoomMode = "PUBLIC" | "PRIVATE";

export type LiveRoomStatus = "ACTIVE" | "PAUSED" | "ENDED";

export interface LiveRoom {
  id: string;
  hostUserId: string;
  roomCode: string;
  title: string;
  description: string | null;
  mode: LiveRoomMode;
  maxParticipants: number;
  currentParticipantCount: number;
  availableSlots: number;
  status: LiveRoomStatus;
  scheduledStartAt: string | null;
  startedAt: string | null;
  endedAt: string | null;
  createdAt: string;
}

export interface LiveRoomSummary {
  id: string;
  roomCode: string;
  title: string;
  mode: LiveRoomMode;
  status: LiveRoomStatus;
  maxParticipants: number;
  currentParticipantCount: number;
  createdAt: string;
  endedAt: string | null;
}

export interface LiveRoomExistsResponse {
  roomCode: string;
  exists: boolean;
  active: boolean;
}

export interface LiveRoomViewerStatus {
  viewerUserId: string;
  host: boolean;
  participant: boolean;
  pendingRequest: boolean;
  pendingRequestId: string | null;
  pendingStatus: JoinRequestStatus | null;
  roomStatus: LiveRoomStatus;
  roomMode: LiveRoomMode;
  roomCode: string;
  hostUserId: string;
  createdAt: string;
}

export interface CreateLiveRoomRequest {
  title: string;
  description?: string;
  mode: LiveRoomMode;
  maxParticipants?: number;
}

export interface ParticipantSummary {
  participantId: string;
  userId: string;
  displayName: string;
  email: string | null;
  roleAtJoin: string;
  joinedAt: string;
  micMuted: boolean;
  cameraOff: boolean;
  lastSeenAt: string;
}

export interface ListMyRoomsParams {
  page: number;
  size: number;
  status?: LiveRoomStatus;
}

export type JoinRequestStatus = "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED";

export interface LiveRoomJoinRequest {
  id: string;
  roomCode: string;
  userId: string;
  displayName: string;
  message: string | null;
  status: JoinRequestStatus;
  decisionReason: string | null;
  decidedByUserId: string | null;
  decidedAt: string | null;
  createdAt: string;
}

export interface CreateJoinRequestBody {
  displayName?: string;
  message?: string;
}

export interface JoinRequestDecisionBody {
  reason?: string;
}

export interface MediaStateUpdateBody {
  micMuted: boolean;
  cameraOff: boolean;
}

export type ParticipantWsEventType =
  | "PARTICIPANT_JOINED"
  | "PARTICIPANT_LEFT"
  | "MEDIA_STATE_CHANGED"
  | "ROOM_ENDED"
  | "HAND_RAISED"
  | "HAND_LOWERED";

export interface ParticipantWsEvent {
  type: ParticipantWsEventType;
  roomCode?: string;
  userId?: string;
  displayName?: string;
  roleAtJoin?: string;
  currentCount?: number;
  maxParticipants?: number;
  availableSlots?: number;
  timestamp?: string;
  micMuted?: boolean;
  cameraOff?: boolean;
}

export interface MediaStateChangedWsEvent {
  type: "MEDIA_STATE_CHANGED";
  roomCode: string;
  userId: string;
  displayName: string;
  micMuted: boolean;
  cameraOff: boolean;
  timestamp: string;
}

export interface PeerJoinedWsEvent {
  type: "PEER_JOINED";
  roomCode: string;
  userId: string;
  displayName: string;
  timestamp: string;
}

export interface PeerLeftWsEvent {
  type: "PEER_LEFT";
  roomCode: string;
  userId: string;
  timestamp: string;
}

export type PeerWsEvent = PeerJoinedWsEvent | PeerLeftWsEvent;

export interface RoomStateParticipant {
  userId: string;
  displayName: string;
}

export interface RoomStateWsEvent {
  type: "ROOM_STATE";
  roomCode: string;
  participants: RoomStateParticipant[];
  timestamp: string;
}

export interface JoinRequestCreatedWsEvent {
  type: "JOIN_REQUEST_CREATED";
  roomCode: string;
  requestId: string;
  requesterUserId: string;
  displayName: string;
  message: string;
  timestamp: string;
}

export type JoinRequestDecisionStatus = "APPROVED" | "REJECTED" | "CANCELLED";

export interface JoinRequestDecidedWsEvent {
  type: "JOIN_REQUEST_DECIDED";
  roomCode: string;
  requestId: string;
  status: JoinRequestDecisionStatus;
  reason: string;
  timestamp: string;
}

export interface PeerSignalEnvelope {
  type: "OFFER" | "ANSWER" | "ICE";
  roomCode: string;
  fromUserId: string;
  toUserId: string;
  payload: PeerSignalBody;
}

export interface PeerSignalBody {
  sdp?: string;
  candidate?: string;
  sdpMid?: string | null;
  sdpMLineIndex?: number | null;
  usernameFragment?: string | null;
}

export interface PeerSignalOutgoing {
  toUserId: string;
  payload: PeerSignalBody;
}

export interface MediaDeviceInfo {
  deviceId: string;
  label: string;
}

export type GuestPhase =
  | { kind: "ASK" }
  | { kind: "WAITING"; request: LiveRoomJoinRequest }
  | { kind: "REJECTED"; request: LiveRoomJoinRequest; reason: string }
  | { kind: "IN_ROOM" };

export type PlaybackStatus =
  | "EMPTY"
  | "PAUSED"
  | "PLAYING"
  | "ENDED"
  | "CLEARED";

export interface SongPlaybackSummary {
  songId: string;
  ownerUserId: string;
  title: string;
  artist: string | null;
  album: string | null;
  durationSeconds: number | null;
  format: string;
  processable: boolean;
}

export interface PlaybackSnapshot {
  roomCode: string;
  status: PlaybackStatus;
  positionSeconds: number;
  effectiveAt: string;
  version: number;
  changedByUserId: string | null;
  changedAt: string;
  empty: boolean;
  song: SongPlaybackSummary | null;
  playbackRate: string;
  loopMode: "OFF" | "ONE";
  shuffleEnabled: boolean;
}

export interface PlaybackSongPayload {
  songId: string;
  ownerUserId: string;
  title: string;
  artist: string | null;
  album: string | null;
  durationSeconds: number | null;
  format: string;
  processable: boolean;
}

export interface PlaybackStateChangedWsEvent {
  type: "PLAYBACK_STATE_CHANGED";
  roomCode: string;
  status: PlaybackStatus;
  positionSeconds: number;
  effectiveAt: string;
  version: number;
  empty: boolean;
  song: PlaybackSongPayload | null;
  changedByUserId: string | null;
  changedAt: string;
  timestamp: string;
  playbackRate: string;
  loopMode: "OFF" | "ONE";
  shuffleEnabled: boolean;
}

export interface PlaybackStateWsEvent {
  type: "PLAYBACK_STATE";
  roomCode: string;
  status: PlaybackStatus;
  positionSeconds: number;
  effectiveAt: string;
  version: number;
  empty: boolean;
  song: PlaybackSongPayload | null;
  changedByUserId: string | null;
  changedAt: string;
  timestamp: string;
  playbackRate: string;
  loopMode: "OFF" | "ONE";
  shuffleEnabled: boolean;
}

export type PlaybackWsEvent =
  | PlaybackStateChangedWsEvent
  | PlaybackStateWsEvent;
