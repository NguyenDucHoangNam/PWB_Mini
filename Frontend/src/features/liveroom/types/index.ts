export type RoomStatus = "ACTIVE" | "ENDED";

export type EndedReason =
  | "MANUAL"
  | "OWNER_GRACE_EXPIRED"
  | "EMPTY_TIMEOUT"
  | "FORCE_ROLE_CHANGE";

export type ParticipantRole = "OWNER" | "PARTICIPANT";

export type ParticipantState =
  | "ACTIVE"
  | "RECONNECTING"
  | "OFFLINE"
  | "LEFT"
  | "KICKED"
  | "ENDED";

export type MicState = "UNMUTED" | "SELF_MUTED" | "MUTED_BY_OWNER";

export type JoinRequestState =
  | "PENDING"
  | "APPROVED"
  | "REJECTED_BY_OWNER"
  | "REJECTED_BY_CAPACITY"
  | "CANCELLED"
  | "EXPIRED"
  | "LOCKED";

export type RejectionReason =
  | "OWNER_REJECT"
  | "CAPACITY_FULL"
  | "ROOM_ENDED"
  | "USER_CANCELLED"
  | "RATE_LIMIT";

export type PlaybackStatus = "PLAYING" | "PAUSED";

export const ROOM_MIN_CAPACITY = 1;
export const ROOM_MAX_CAPACITY = 7;
export const ROOM_DEFAULT_CAPACITY = 7;
export const ROOM_MIN_GRACE_SECONDS = 30;
export const ROOM_MAX_GRACE_SECONDS = 1800;
export const ROOM_DEFAULT_GRACE_SECONDS = 60;
export const ROOM_CODE_LENGTH = 6;
export const CHAT_MAX_CONTENT_LENGTH = 500;
export const TRACK_COMMENT_MAX_LENGTH = 200;
export const CHAT_HISTORY_MAX_SIZE = 200;
export const JOIN_REJECT_LIMIT = 3;

export interface Room {
  id: string;
  ownerId: string;
  roomCode: string;
  roomCodeDisplay: string;
  roomName: string;
  status: RoomStatus;
  maxParticipants: number;
  effectiveMaxParticipants: number;
  currentParticipantCount: number;
  ownerGraceSeconds: number;
  reservedOwnerSlot: boolean;
  ownerLeftAt: string | null;
  currentCycleId: string | null;
  reopenedCount: number;
  lastReopenedAt: string | null;
  previousEndedAt: string | null;
  endedAt: string | null;
  endedReason: EndedReason | null;
  canUndoEnd: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface RoomLookup {
  roomId: string;
  roomCode: string;
  roomCodeDisplay: string;
  roomName: string;
  status: RoomStatus;
  maxParticipants: number;
  currentParticipantCount: number;
  full: boolean;
}

export interface Participant {
  id: string;
  userId: string;
  userEmail: string;
  avatarUrl: string | null;
  roomRole: ParticipantRole;
  state: ParticipantState;
  joinedAt: string;
  leftAt: string | null;
  cameraOn: boolean;
  micOn: boolean;
  micState: MicState;
  micUnmuteCooldownUntil: string | null;
  lastInteractionAt: string | null;
  oldest: boolean;
  newest: boolean;
}

export interface JoinRequest {
  id: string;
  roomId: string;
  userId: string;
  userEmail: string;
  avatarUrl: string | null;
  state: JoinRequestState;
  rejectionReason: RejectionReason | null;
  createdAt: string;
  decidedAt: string | null;
  decidedBy: string | null;
  rejectCountByOwner: number;
  attemptsRemaining: number;
}

export interface ChatMessage {
  id: string;
  roomId: string;
  cycleId: string;
  userId: string;
  userEmail: string;
  content: string;
  sentAt: string;
}

export interface TrackComment {
  id: string;
  songId: string;
  userId: string;
  userEmail: string;
  content: string;
  positionSeconds: number;
  createdAt: string;
}

export interface ChatHistory {
  messages: ChatMessage[];
  hasMore: boolean;
  nextCursor: string | null;
}

export interface IceServerConfig {
  urls: string[];
  username: string | null;
  credential: string | null;
}

export interface RtcConfig {
  iceServers: IceServerConfig[];
  maxMeshPeers: number;
  maxSdpLength: number;
  maxCandidateLength: number;
}

export interface RoomAudioUrl {
  songId: string;
  url: string;
  expiresAt: string;
}

export interface CreateRoomInput {
  roomName: string;
  maxParticipants?: number;
  ownerGraceSeconds?: number;
}

export interface CreateJoinRequestInput {
  idempotencyKey: string;
}

export interface UpdateMediaStateInput {
  cameraOn?: boolean;
  micOn?: boolean;
}

export interface ModerateParticipantInput {
  reason?: string;
}