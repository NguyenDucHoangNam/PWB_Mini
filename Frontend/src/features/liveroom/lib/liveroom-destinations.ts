import { API_BASE_URL } from "@/lib/constants";

function backendOrigin(): string {
  try {
    return new URL(API_BASE_URL, typeof window === "undefined" ? "http://localhost" : window.location.href)
      .origin;
  } catch {
    return "http://localhost:8080";
  }
}

export const LIVEROOM_WS_HTTP_URL = `${backendOrigin()}/ws`;

export const LIVEROOM_WS_URL = LIVEROOM_WS_HTTP_URL.replace(/^http/, "ws");

export const PERSONAL_QUEUE = "/user/queue/liveroom";
export const PERSONAL_ERROR_QUEUE = "/user/queue/liveroom/errors";
export const PERSONAL_RTC_QUEUE = "/user/queue/liveroom/rtc";

export function roomTopic(roomId: string): string {
  return `/topic/liveroom/${roomId}`;
}

export function roomChatTopic(roomId: string): string {
  return `/topic/liveroom/${roomId}/chat`;
}

export function roomMusicTopic(roomId: string): string {
  return `/topic/liveroom/${roomId}/music`;
}

const app = (roomId: string, suffix: string) => `/app/liveroom/${roomId}/${suffix}`;

export const appDestinations = {
  chatSend: (roomId: string) => app(roomId, "chat/send"),
  musicPlay: (roomId: string) => app(roomId, "music/play"),
  musicPause: (roomId: string) => app(roomId, "music/pause"),
  musicSeek: (roomId: string) => app(roomId, "music/seek"),
  musicVolume: (roomId: string) => app(roomId, "music/volume"),
  musicGetState: (roomId: string) => app(roomId, "music/get-state"),
  commentAdd: (roomId: string) => app(roomId, "comments/add"),
  commentsGet: (roomId: string) => app(roomId, "comments/get"),
  rtcOffer: (roomId: string) => app(roomId, "rtc/offer"),
  rtcAnswer: (roomId: string) => app(roomId, "rtc/answer"),
  rtcIce: (roomId: string) => app(roomId, "rtc/ice"),
} as const;

export const liveroomApi = {
  rooms: "/liveroom/rooms",
  roomSearch: "/liveroom/rooms/search",
  room: (roomId: string) => `/liveroom/rooms/${roomId}`,
  roomByCode: (roomCode: string) => `/liveroom/rooms/by-code/${roomCode}`,
  end: (roomId: string) => `/liveroom/rooms/${roomId}/end`,
  undoEnd: (roomId: string) => `/liveroom/rooms/${roomId}/undo-end`,
  reopen: (roomId: string) => `/liveroom/rooms/${roomId}/reopen`,
  joinRequests: (roomId: string) => `/liveroom/rooms/${roomId}/join-requests`,
  myJoinRequest: (roomId: string) => `/liveroom/rooms/${roomId}/join-requests/me`,
  joinRequest: (roomId: string, requestId: string) =>
    `/liveroom/rooms/${roomId}/join-requests/${requestId}`,
  approveJoinRequest: (roomId: string, requestId: string) =>
    `/liveroom/rooms/${roomId}/join-requests/${requestId}/approve`,
  rejectJoinRequest: (roomId: string, requestId: string) =>
    `/liveroom/rooms/${roomId}/join-requests/${requestId}/reject`,
  participants: (roomId: string) => `/liveroom/rooms/${roomId}/participants`,
  me: (roomId: string) => `/liveroom/rooms/${roomId}/participants/me`,
  myMedia: (roomId: string) => `/liveroom/rooms/${roomId}/participants/me/media`,
  kick: (roomId: string, targetUserId: string) =>
    `/liveroom/rooms/${roomId}/participants/${targetUserId}/kick`,
  mute: (roomId: string, targetUserId: string) =>
    `/liveroom/rooms/${roomId}/participants/${targetUserId}/mute`,
  chatMessages: (roomId: string) => `/liveroom/rooms/${roomId}/chat/messages`,
  rtcConfig: (roomId: string) => `/liveroom/rooms/${roomId}/rtc/config`,
  audioUrl: (roomId: string) => `/liveroom/rooms/${roomId}/music/audio-url`,
} as const;