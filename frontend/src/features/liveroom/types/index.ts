export type LiveRoomMode = "PUBLIC" | "PRIVATE" | "INVITE_ONLY" | "PASSWORD";

export type LiveRoomStatus = "ACTIVE" | "PAUSED" | "ENDED";

export interface LiveRoom {
  id: string;
  hostUserId: string;
  roomCode: string;
  title: string;
  description: string | null;
  mode: LiveRoomMode;
  passwordProtected: boolean;
  maxParticipants: number;
  currentParticipantCount: number;
  availableSlots: number;
  status: LiveRoomStatus;
  scheduledStartAt: string | null;
  startedAt: string | null;
  endedAt: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
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
  passwordRequired: boolean;
}

export interface CreateLiveRoomRequest {
  title: string;
  description?: string;
  mode: LiveRoomMode;
  password?: string;
  maxParticipants?: number;
  scheduledStartAt?: string;
}

export interface UpdateLiveRoomSettingsRequest {
  title?: string;
  description?: string;
  mode?: LiveRoomMode;
  password?: string;
  maxParticipants?: number;
}

export interface LiveRoomJoinResponse {
  roomCode: string;
  title: string;
  hostUserId: string;
  participantId: string;
  displayName: string;
  roleAtJoin: string;
  joinedAt: string;
  currentParticipantCount: number;
  maxParticipants: number;
  availableSlots: number;
}

export interface ParticipantSummary {
  participantId: string;
  userId: string;
  displayName: string;
  roleAtJoin: string;
  joinedAt: string;
}

export interface JoinLiveRoomRequest {
  displayName?: string;
}

export interface ListMyRoomsParams {
  page: number;
  size: number;
  status?: LiveRoomStatus;
}

export type ParticipantWsEventType = "PARTICIPANT_JOINED" | "PARTICIPANT_LEFT" | "ROOM_STATE";

export interface ParticipantWsEvent {
  type: ParticipantWsEventType;
  roomCode?: string;
  userId?: string;
  displayName?: string;
  currentCount?: number;
  maxParticipants?: number;
  timestamp?: string;
  participant?: ParticipantSummary;
}
