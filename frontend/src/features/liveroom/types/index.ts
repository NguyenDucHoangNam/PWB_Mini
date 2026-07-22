export type LiveRoomMode = "PUBLIC";

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
  maxParticipants?: number;
  scheduledStartAt?: string;
}

export interface UpdateLiveRoomSettingsRequest {
  title?: string;
  description?: string;
  maxParticipants?: number;
}

export interface ParticipantSummary {
  participantId: string;
  userId: string;
  displayName: string;
  roleAtJoin: string;
  joinedAt: string;
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

export type ParticipantWsEventType = "PARTICIPANT_JOINED" | "PARTICIPANT_LEFT";

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
