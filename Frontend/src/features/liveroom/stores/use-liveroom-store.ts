"use client";

import { create } from "zustand";
import type { ApiResponse } from "@/types/api";
import type { ConnectionStatus } from "../lib/liveroom-socket";
import type { LiveroomEvent, MusicStateData } from "../types/events";
import type {
  ChatMessage,
  EndedReason,
  JoinRequest,
  Participant,
  Room,
  RtcConfig,
} from "../types";

export interface PendingChatMessage {
  tempId: string;
  content: string;
  sentAt: string;
  failed: boolean;
}

export interface KickedInfo {
  userId: string;
  kickedBy: string;
  reason: string | null;
  cooldownUntil: string | null;
}

interface RoomSlice {
  status: "ACTIVE" | "ENDED";
  currentParticipantCount: number;
  maxParticipants: number;
  effectiveMaxParticipants: number;
  reservedOwnerSlot: boolean;
  ownerAbsent: boolean;
  graceExpiresAt: string | null;
  endedAt: string | null;
  endedReason: EndedReason | null;
  roomName: string;
  roomCode: string;
  ownerId: string | null;
}

interface LiveroomState {
  roomId: string | null;
  myUserId: string | null;
  isOwner: boolean;

  connection: {
    status: ConnectionStatus;
    attempt: number;
    lastFrameError: ApiResponse<unknown> | null;
  };

  buffering: boolean;
  buffer: LiveroomEvent[];

  room: RoomSlice;
  participants: Record<string, Participant>;
  joinRequests: Record<string, JoinRequest>;

  chat: {
    messages: ChatMessage[];
    seenIds: Set<string>;
    hasMore: boolean;
    nextCursor: string | null;
    pending: PendingChatMessage[];
  };

  music: {
    state: MusicStateData | null;
    sequenceNumber: number;
    reloadToken: number;
  };

  rtc: {
    config: RtcConfig | null;
    peers: Record<string, { state: RTCPeerConnectionState; stream: MediaStream | null }>;
  };

  lifecycle: {
    endingAt: number | null;
    kicked: KickedInfo | null;
    revivedAt: string | null;
  };

  reset: (roomId: string | null, myUserId: string | null, isOwner: boolean) => void;
  setConnection: (status: ConnectionStatus, attempt: number) => void;
  setFrameError: (error: ApiResponse<unknown> | null) => void;
  beginBuffering: () => void;
  drainBuffer: () => void;
  applyEvent: (event: LiveroomEvent) => void;
  applyRoom: (room: Room) => void;
  applyParticipants: (participants: Participant[]) => void;
  applyJoinRequests: (requests: JoinRequest[]) => void;
  removeJoinRequest: (requestId: string) => void;
  applyChatHistory: (
    messages: ChatMessage[],
    hasMore: boolean,
    nextCursor: string | null,
  ) => void;
  prependChatHistory: (
    messages: ChatMessage[],
    hasMore: boolean,
    nextCursor: string | null,
  ) => void;
  applyRtcConfig: (config: RtcConfig) => void;
  addPendingChat: (pending: PendingChatMessage) => void;
  failPendingChat: (tempId: string) => void;
  removePendingChat: (tempId: string) => void;
  setPeerState: (userId: string, state: RTCPeerConnectionState) => void;
  setPeerStream: (userId: string, stream: MediaStream | null) => void;
  removePeer: (userId: string) => void;
  clearEnding: () => void;
}

const EMPTY_ROOM: RoomSlice = {
  status: "ACTIVE",
  currentParticipantCount: 0,
  maxParticipants: 0,
  effectiveMaxParticipants: 0,
  reservedOwnerSlot: false,
  ownerAbsent: false,
  graceExpiresAt: null,
  endedAt: null,
  endedReason: null,
  roomName: "",
  roomCode: "",
  ownerId: null,
};

const ROOM_END_GRACE_MS = 5000;

function synthesizeParticipant(
  existing: Participant | undefined,
  patch: Partial<Participant> & { userId: string },
): Participant {
  return {
    id: existing?.id ?? patch.userId,
    userId: patch.userId,
    userEmail: patch.userEmail ?? existing?.userEmail ?? "",
    roomRole: patch.roomRole ?? existing?.roomRole ?? "PARTICIPANT",
    state: patch.state ?? existing?.state ?? "ACTIVE",
    joinedAt: patch.joinedAt ?? existing?.joinedAt ?? new Date().toISOString(),
    leftAt: patch.leftAt ?? existing?.leftAt ?? null,
    cameraOn: patch.cameraOn ?? existing?.cameraOn ?? false,
    micOn: patch.micOn ?? existing?.micOn ?? false,
    micState: patch.micState ?? existing?.micState ?? "SELF_MUTED",
    micUnmuteCooldownUntil:
      patch.micUnmuteCooldownUntil ?? existing?.micUnmuteCooldownUntil ?? null,
    lastInteractionAt: patch.lastInteractionAt ?? existing?.lastInteractionAt ?? null,
    oldest: false,
    newest: false,
  };
}

export const useLiveroomStore = create<LiveroomState>((set, get) => ({
  roomId: null,
  myUserId: null,
  isOwner: false,
  connection: { status: "idle", attempt: 0, lastFrameError: null },
  buffering: false,
  buffer: [],
  room: EMPTY_ROOM,
  participants: {},
  joinRequests: {},
  chat: { messages: [], seenIds: new Set(), hasMore: false, nextCursor: null, pending: [] },
  music: { state: null, sequenceNumber: -1, reloadToken: 0 },
  rtc: { config: null, peers: {} },
  lifecycle: { endingAt: null, kicked: null, revivedAt: null },

  reset: (roomId, myUserId, isOwner) =>
    set({
      roomId,
      myUserId,
      isOwner,
      connection: { status: "idle", attempt: 0, lastFrameError: null },
      buffering: false,
      buffer: [],
      room: EMPTY_ROOM,
      participants: {},
      joinRequests: {},
      chat: { messages: [], seenIds: new Set(), hasMore: false, nextCursor: null, pending: [] },
      music: { state: null, sequenceNumber: -1, reloadToken: 0 },
      rtc: { config: null, peers: {} },
      lifecycle: { endingAt: null, kicked: null, revivedAt: null },
    }),

  setConnection: (status, attempt) =>
    set((state) => ({ connection: { ...state.connection, status, attempt } })),

  setFrameError: (error) =>
    set((state) => ({ connection: { ...state.connection, lastFrameError: error } })),

  beginBuffering: () => set({ buffering: true, buffer: [] }),

  drainBuffer: () => {
    const queued = get().buffer;
    set({ buffering: false, buffer: [] });
    queued.forEach((event) => get().applyEvent(event));
  },

  applyEvent: (event) => {
    const state = get();
    if (state.roomId && event.roomId && state.roomId !== event.roomId) return;
    if (state.buffering) {
      set({ buffer: [...state.buffer, event] });
      return;
    }
    set((current) => reduce(current, event));
  },

  applyRoom: (room) =>
    set((state) => ({
      room: {
        ...state.room,
        status: room.status,
        currentParticipantCount: room.currentParticipantCount,
        maxParticipants: room.maxParticipants,
        effectiveMaxParticipants: room.effectiveMaxParticipants,
        reservedOwnerSlot: room.reservedOwnerSlot,
        ownerAbsent: Boolean(room.ownerLeftAt),
        endedAt: room.endedAt,
        endedReason: room.endedReason,
        roomName: room.roomName,
        roomCode: room.roomCode,
        ownerId: room.ownerId,
      },
      isOwner: state.myUserId ? room.ownerId === state.myUserId : state.isOwner,
    })),

  applyParticipants: (participants) =>
    set(() => ({
      participants: Object.fromEntries(participants.map((p) => [p.userId, p])),
    })),

  applyJoinRequests: (requests) =>
    set(() => ({
      joinRequests: Object.fromEntries(requests.map((r) => [r.id, r])),
    })),


  removeJoinRequest: (requestId) =>
    set((state) => {
      const joinRequests = { ...state.joinRequests };
      delete joinRequests[requestId];
      return { joinRequests };
    }),

  applyChatHistory: (messages, hasMore, nextCursor) =>
    set((state) => ({
      chat: {
        ...state.chat,
        messages,
        seenIds: new Set(messages.map((m) => m.id)),
        hasMore,
        nextCursor,
      },
    })),

  prependChatHistory: (messages, hasMore, nextCursor) =>
    set((state) => {
      const seen = new Set(state.chat.seenIds);
      const fresh = messages.filter((m) => !seen.has(m.id));
      fresh.forEach((m) => seen.add(m.id));
      return {
        chat: {
          ...state.chat,
          messages: [...fresh, ...state.chat.messages],
          seenIds: seen,
          hasMore,
          nextCursor,
        },
      };
    }),

  applyRtcConfig: (config) => set((state) => ({ rtc: { ...state.rtc, config } })),

  addPendingChat: (pending) =>
    set((state) => ({ chat: { ...state.chat, pending: [...state.chat.pending, pending] } })),

  failPendingChat: (tempId) =>
    set((state) => ({
      chat: {
        ...state.chat,
        pending: state.chat.pending.map((p) =>
          p.tempId === tempId ? { ...p, failed: true } : p,
        ),
      },
    })),

  removePendingChat: (tempId) =>
    set((state) => ({
      chat: { ...state.chat, pending: state.chat.pending.filter((p) => p.tempId !== tempId) },
    })),

  setPeerState: (userId, peerState) =>
    set((state) => ({
      rtc: {
        ...state.rtc,
        peers: {
          ...state.rtc.peers,
          [userId]: { state: peerState, stream: state.rtc.peers[userId]?.stream ?? null },
        },
      },
    })),

  setPeerStream: (userId, stream) =>
    set((state) => ({
      rtc: {
        ...state.rtc,
        peers: {
          ...state.rtc.peers,
          [userId]: { state: state.rtc.peers[userId]?.state ?? "new", stream },
        },
      },
    })),

  removePeer: (userId) =>
    set((state) => {
      const peers = { ...state.rtc.peers };
      delete peers[userId];
      return { rtc: { ...state.rtc, peers } };
    }),

  clearEnding: () =>
    set((state) => ({ lifecycle: { ...state.lifecycle, endingAt: null } })),
}));

function reduce(state: LiveroomState, event: LiveroomEvent): Partial<LiveroomState> {
  switch (event.type) {
    case "PARTICIPANT_JOINED": {
      const { userId } = event.data;
      return {
        participants: {
          ...state.participants,
          [userId]: synthesizeParticipant(state.participants[userId], {
            userId,
            userEmail: event.data.userEmail,
            roomRole: event.data.roomRole,
            joinedAt: event.data.joinedAt,
            state: "ACTIVE",
            leftAt: null,
          }),
        },
      };
    }

    case "PARTICIPANT_LEFT": {
      const participants = { ...state.participants };
      delete participants[event.data.userId];
      return { participants };
    }

    case "PARTICIPANT_KICKED": {
      const participants = { ...state.participants };
      delete participants[event.data.userId];
      const isMe = state.myUserId === event.data.userId;
      return {
        participants,
        lifecycle: isMe
          ? {
              ...state.lifecycle,
              kicked: {
                userId: event.data.userId,
                kickedBy: event.data.kickedBy,
                reason: event.data.reason,
                cooldownUntil: event.data.cooldownUntil,
              },
            }
          : state.lifecycle,
      };
    }

    case "MEDIA_STATE_CHANGED": {
      const { userId } = event.data;
      const existing = state.participants[userId];
      if (!existing) return {};
      return {
        participants: {
          ...state.participants,
          [userId]: {
            ...existing,
            cameraOn: event.data.cameraOn,
            micOn: event.data.micOn,
            micState: event.data.micState,
          },
        },
      };
    }

    case "PARTICIPANT_MIC_MUTED_BY_OWNER": {
      const existing = state.participants[event.data.userId];
      if (!existing) return {};
      return {
        participants: {
          ...state.participants,
          [event.data.userId]: {
            ...existing,
            micOn: false,
            micState: "MUTED_BY_OWNER",
            micUnmuteCooldownUntil: event.data.cooldownUntil,
          },
        },
      };
    }

    case "PARTICIPANT_MIC_UNMUTED": {
      const existing = state.participants[event.data.userId];
      if (!existing) return {};
      return {
        participants: {
          ...state.participants,
          [event.data.userId]: { ...existing, micOn: true, micState: "UNMUTED" },
        },
      };
    }

    case "OWNER_LEFT":
      return {
        room: {
          ...state.room,
          ownerAbsent: true,
          graceExpiresAt: event.data.graceExpiresAt,
        },
      };

    case "OWNER_REJOINED":
      return { room: { ...state.room, ownerAbsent: false, graceExpiresAt: null } };

    case "ROOM_CAPACITY_CHANGED":
      return {
        room: {
          ...state.room,
          currentParticipantCount: event.data.currentCount,
          maxParticipants: event.data.maxParticipants,
          effectiveMaxParticipants: event.data.effectiveMaxParticipants,
          reservedOwnerSlot: event.data.reservedOwnerSlot,
        },
      };

    case "CAPACITY_REACHED":
      return {
        room: {
          ...state.room,
          currentParticipantCount: event.data.currentCount,
          effectiveMaxParticipants: event.data.effectiveMaxParticipants,
        },
      };

    case "JOIN_REQUEST_CREATED":
      return {
        joinRequests: {
          ...state.joinRequests,
          [event.data.requestId]: {
            id: event.data.requestId,
            roomId: event.roomId,
            userId: event.data.userId,
            userEmail: event.data.userEmail,
            state: "PENDING",
            rejectionReason: null,
            createdAt: event.data.createdAt,
            decidedAt: null,
            decidedBy: null,
            rejectCountByOwner: 0,
            attemptsRemaining: 0,
          },
        },
      };

    case "JOIN_REQUEST_CANCELLED": {
      const joinRequests = { ...state.joinRequests };
      delete joinRequests[event.data.requestId];
      return { joinRequests };
    }

    case "REQUEST_APPROVED":
    case "REQUEST_REJECTED_BY_OWNER":
    case "REQUEST_REJECTED_BY_CAPACITY": {
      const joinRequests = { ...state.joinRequests };
      delete joinRequests[event.data.requestId];
      return { joinRequests };
    }

    case "REQUEST_LOCKED":
      return {};

    case "CHAT_MESSAGE_RECEIVED": {
      if (state.chat.seenIds.has(event.data.messageId)) return {};
      const message: ChatMessage = {
        id: event.data.messageId,
        roomId: event.roomId,
        cycleId: event.data.cycleId,
        userId: event.data.userId,
        userEmail: event.data.userEmail,
        content: event.data.content,
        sentAt: event.data.sentAt,
      };
      const seenIds = new Set(state.chat.seenIds);
      seenIds.add(message.id);
      return {
        chat: {
          ...state.chat,
          messages: [...state.chat.messages, message],
          seenIds,
          pending: state.chat.pending.filter((p) => p.content !== message.content),
        },
      };
    }

    case "MUSIC_PLAYBACK_STATE_CHANGED":
    case "MUSIC_SONG_CHANGED": {
      if (event.data.sequenceNumber <= state.music.sequenceNumber) return {};
      const songChanged = event.type === "MUSIC_SONG_CHANGED";
      return {
        music: {
          state: event.data,
          sequenceNumber: event.data.sequenceNumber,
          reloadToken: songChanged ? state.music.reloadToken + 1 : state.music.reloadToken,
        },
        room: { ...state.room, ownerAbsent: event.data.ownerAbsent },
      };
    }

    case "RTC_OFFER":
    case "RTC_ANSWER":
    case "RTC_ICE_CANDIDATE":
      return {};

    case "ROOM_MANUAL_ENDED":
      return {
        room: { ...state.room, status: "ENDED", endedAt: event.data.endedAt },
        lifecycle: { ...state.lifecycle, endingAt: Date.now() + ROOM_END_GRACE_MS },
      };

    case "ROOM_AUTO_ENDED":
      return {
        room: {
          ...state.room,
          status: "ENDED",
          endedAt: event.data.endedAt,
          endedReason: event.data.reason,
        },
        lifecycle: { ...state.lifecycle, endingAt: Date.now() },
      };

    case "ROOM_REVIVED":
      return {
        room: { ...state.room, status: "ACTIVE", endedAt: null, endedReason: null },
        lifecycle: {
          ...state.lifecycle,
          endingAt: null,
          revivedAt: event.data.revivedAt,
        },
      };

    case "ROOM_REOPENED":
      return { room: { ...state.room, status: "ACTIVE", endedAt: null, endedReason: null } };

    default: {
      const exhaustive: never = event;
      void exhaustive;
      return {};
    }
  }
}