import { Client, type IMessage, type StompSubscription } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { API_BASE_URL } from "@/lib/constants";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";
import type {
  JoinRequestCreatedWsEvent,
  JoinRequestDecidedWsEvent,
  MediaStateChangedWsEvent,
  ParticipantWsEvent,
  PeerSignalEnvelope,
  PeerWsEvent,
  PlaybackWsEvent,
  RoomStateWsEvent,
} from "../types";

const WS_PATH = "/ws/liveroom";

function resolveWsUrl(): string {
  const httpBase = API_BASE_URL.replace(/\/api\/v\d+$/, "");
  if (httpBase.startsWith("https://")) {
    return `https://${httpBase.slice("https://".length)}${WS_PATH}`;
  }
  if (httpBase.startsWith("http://")) {
    return `http://${httpBase.slice("http://".length)}${WS_PATH}`;
  }
  return `${httpBase}${WS_PATH}`;
}

const WS_URL = resolveWsUrl();

let sharedClient: Client | null = null;
let currentToken: string | null = null;
let isReconnecting = false;

interface TrackedSubscription {
  destination: string;
  handleFrame: (frame: IMessage) => void;
  subscription: StompSubscription | null;
  filter?: (raw: unknown) => boolean;
}

const trackedSubs: Set<TrackedSubscription> = new Set();

interface OutboxEntry {
  destination: string;
  body: unknown;
  enqueuedAt: number;
  attempts: number;
}

const outbox: OutboxEntry[] = [];
const MAX_OUTBOX_SIZE = 100;
const MAX_OUTBOX_AGE_MS = 30_000;
const MAX_OUTBOX_ATTEMPTS = 3;

function flushOutbox(): void {
  if (!sharedClient || !sharedClient.connected) return;
  const now = Date.now();
  for (let i = outbox.length - 1; i >= 0; i--) {
    const entry = outbox[i];
    if (now - entry.enqueuedAt > MAX_OUTBOX_AGE_MS || entry.attempts >= MAX_OUTBOX_ATTEMPTS) {
      outbox.splice(i, 1);
      continue;
    }
    try {
      sharedClient.publish({ destination: entry.destination, body: JSON.stringify(entry.body) });
      outbox.splice(i, 1);
    } catch (err) {
      entry.attempts++;
      console.warn(`[WS-Outbox] Failed to flush (attempt ${entry.attempts}):`, err);
    }
  }
}

function enqueueOutbox(destination: string, body: unknown): void {
  if (outbox.length >= MAX_OUTBOX_SIZE) {
    outbox.shift();
  }
  outbox.push({ destination, body, enqueuedAt: Date.now(), attempts: 0 });
}

export function getStompClient(accessToken: string): Client {
  if (sharedClient && currentToken === accessToken) {
    if (sharedClient.connected || isReconnecting) {
      return sharedClient;
    }
  }

  for (const tracked of trackedSubs) {
    if (tracked.subscription) {
      try {
        tracked.subscription.unsubscribe();
      } catch {
        /* ignore */
      }
      tracked.subscription = null;
    }
  }

  if (sharedClient) {
    sharedClient.deactivate();
    sharedClient = null;
  }

  isReconnecting = true;
  sharedClient = new Client({
    webSocketFactory: () => new SockJS(WS_URL) as unknown as WebSocket,
    connectHeaders: {
      Authorization: `Bearer ${accessToken}`,
    },
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    debug: () => {},
    onConnect: () => {
      isReconnecting = false;
      flushOutbox();
      reattachAllSubscriptions();
    },
    onDisconnect: () => {
      isReconnecting = true;
    },
    onWebSocketError: () => {
      isReconnecting = true;
    },
  });
  currentToken = accessToken;
  sharedClient.activate();
  return sharedClient;
}

function reattachAllSubscriptions(): void {
  const client = sharedClient;
  if (!client || !client.connected) return;
  for (const tracked of trackedSubs) {
    if (tracked.subscription) continue;
    tracked.subscription = client.subscribe(tracked.destination, tracked.handleFrame);
  }
}

function ensureClient(): Client | null {
  const token = useAuthStore.getState().accessToken;
  if (!token) return null;
  const client = getStompClient(token);
  if (!client.connected && !isReconnecting) {
    client.activate();
  }
  return client;
}

export interface Subscription {
  unsubscribe: () => void;
}

function subscribeTopic<T>(
  destination: string,
  onEvent: (event: T) => void,
  filter?: (raw: unknown) => boolean,
): Subscription {
  const client = ensureClient();
  if (!client) {
    console.warn("[STOMP-DEBUG] subscribeTopic: no client for", destination);
    return { unsubscribe: () => {} };
  }

  const tracked: TrackedSubscription = {
    destination,
    handleFrame: (frame: IMessage) => {
      try {
        const parsed: unknown = JSON.parse(frame.body);
        console.log("[STOMP-DEBUG] RECEIVED on", destination, "type:", (parsed as any)?.type, "filtered:", filter ? !filter(parsed) : false);
        if (filter && !filter(parsed)) return;
        onEvent(parsed as T);
      } catch {
        /* ignore malformed frames */
      }
    },
    subscription: null,
    filter,
  };

  const attach = () => {
    if (tracked.subscription) return;
    tracked.subscription = client.subscribe(tracked.destination, tracked.handleFrame);
    console.log("[STOMP-DEBUG] ATTACHED subscription to", destination, "subId:", tracked.subscription?.id);
  };

  trackedSubs.add(tracked);
  if (client.connected) {
    attach();
  } else {
    console.log("[STOMP-DEBUG] client NOT connected, queued subscription for", destination);
  }

  return {
    unsubscribe: () => {
      if (tracked.subscription) {
        tracked.subscription.unsubscribe();
        tracked.subscription = null;
      }
      trackedSubs.delete(tracked);
    },
  };
}

export function publishSignal(destination: string, body: unknown): boolean {
  const client = ensureClient();
  if (!client || !client.connected) {
    enqueueOutbox(destination, body);
    return false;
  }
  try {
    client.publish({
      destination,
      body: JSON.stringify(body),
    });
    return true;
  } catch (err) {
    enqueueOutbox(destination, body);
    return false;
  }
}

export function subscribeRoomParticipants(
  roomCode: string,
  onEvent: (event: ParticipantWsEvent) => void,
): Subscription {
  return subscribeTopic<ParticipantWsEvent>(
    `/topic/room/${roomCode}/participants`,
    onEvent,
  );
}

export function subscribeRoomMediaState(
  roomCode: string,
  onEvent: (event: MediaStateChangedWsEvent) => void,
): Subscription {
  return subscribeTopic<MediaStateChangedWsEvent>(
    `/topic/room/${roomCode}/participants`,
    onEvent,
    (raw) =>
      typeof raw === "object" &&
      raw !== null &&
      (raw as { type?: string }).type === "MEDIA_STATE_CHANGED",
  );
}

export function subscribeMyMediaStateNotifications(
  onEvent: (event: MediaStateChangedWsEvent) => void,
): Subscription {
  return subscribeTopic<MediaStateChangedWsEvent>(
    "/user/queue/liveroom-media",
    onEvent,
    (raw) =>
      typeof raw === "object" &&
      raw !== null &&
      (raw as { type?: string }).type === "MEDIA_STATE_CHANGED",
  );
}

export function subscribeRoomPeerEvents(
  roomCode: string,
  onEvent: (event: PeerWsEvent) => void,
): Subscription {
  return subscribeTopic<PeerWsEvent>(
    `/topic/room/${roomCode}/peers`,
    onEvent,
    (raw) => {
      if (typeof raw !== "object" || raw === null) return false;
      const type = (raw as { type?: string }).type;
      return type === "PEER_JOINED" || type === "PEER_LEFT";
    },
  );
}

export function subscribeRoomState(
  roomCode: string,
  onEvent: (event: RoomStateWsEvent) => void,
): Subscription {
  return subscribeTopic<RoomStateWsEvent>(
    `/user/queue/room/${roomCode}/state`,
    onEvent,
    (raw) =>
      typeof raw === "object" &&
      raw !== null &&
      (raw as { type?: string }).type === "ROOM_STATE",
  );
}

export function subscribeSignalingOffers(
  roomCode: string,
  onEvent: (event: PeerSignalEnvelope) => void,
): Subscription {
  return subscribeTopic<PeerSignalEnvelope>(
    `/user/queue/room/${roomCode}/signal/offer`,
    onEvent,
  );
}

export function subscribeSignalingAnswers(
  roomCode: string,
  onEvent: (event: PeerSignalEnvelope) => void,
): Subscription {
  return subscribeTopic<PeerSignalEnvelope>(
    `/user/queue/room/${roomCode}/signal/answer`,
    onEvent,
  );
}

export function subscribeSignalingIce(
  roomCode: string,
  onEvent: (event: PeerSignalEnvelope) => void,
): Subscription {
  return subscribeTopic<PeerSignalEnvelope>(
    `/user/queue/room/${roomCode}/signal/ice`,
    onEvent,
  );
}

export function subscribeRoomJoinRequests(
  roomCode: string,
  onEvent: (event: JoinRequestCreatedWsEvent) => void,
): Subscription {
  return subscribeTopic<JoinRequestCreatedWsEvent>(
    `/topic/room/${roomCode}/join-requests`,
    onEvent,
  );
}

export function subscribeUserJoinRequestDecisions(
  onEvent: (event: JoinRequestDecidedWsEvent) => void,
): Subscription {
  return subscribeTopic<JoinRequestDecidedWsEvent>(
    "/user/queue/join-requests",
    onEvent,
  );
}

export function sendSignalOffer(
  roomCode: string,
  toUserId: string,
  payload: PeerSignalEnvelope["payload"],
): boolean {
  return publishSignal(`/app/room/${roomCode}/signal/offer`, {
    toUserId,
    payload,
  });
}

export function sendSignalAnswer(
  roomCode: string,
  toUserId: string,
  payload: PeerSignalEnvelope["payload"],
): boolean {
  return publishSignal(`/app/room/${roomCode}/signal/answer`, {
    toUserId,
    payload,
  });
}

export function sendSignalIce(
  roomCode: string,
  toUserId: string,
  payload: PeerSignalEnvelope["payload"],
): boolean {
  return publishSignal(`/app/room/${roomCode}/signal/ice`, {
    toUserId,
    payload,
  });
}

export function requestRoomState(roomCode: string): boolean {
  return publishSignal(`/app/room/${roomCode}/state/request`, {});
}

export function subscribeRoomPlayback(
  roomCode: string,
  onEvent: (event: PlaybackWsEvent) => void,
): Subscription {
  return subscribeTopic<PlaybackWsEvent>(
    `/topic/room/${roomCode}/playback`,
    onEvent,
    (raw) => {
      if (typeof raw !== "object" || raw === null) return false;
      const type = (raw as { type?: string }).type;
      return type === "PLAYBACK_STATE_CHANGED";
    },
  );
}

export function subscribeUserPlaybackState(
  roomCode: string,
  onEvent: (event: PlaybackWsEvent) => void,
): Subscription {
  return subscribeTopic<PlaybackWsEvent>(
    `/user/queue/room/${roomCode}/playback/state`,
    onEvent,
    (raw) => {
      if (typeof raw !== "object" || raw === null) return false;
      const type = (raw as { type?: string }).type;
      return type === "PLAYBACK_STATE";
    },
  );
}

export function sendPlaybackPlay(roomCode: string): boolean {
  return publishSignal(`/app/room/${roomCode}/playback/play`, {});
}

export function sendPlaybackPause(roomCode: string): boolean {
  return publishSignal(`/app/room/${roomCode}/playback/pause`, {});
}

export function sendPlaybackStateRequest(roomCode: string): boolean {
  return publishSignal(`/app/room/${roomCode}/playback/state/request`, {});
}

export function sendPlaybackSeek(
  roomCode: string,
  direction: 1 | -1,
): boolean {
  return publishSignal(`/app/room/${roomCode}/playback/seek`, { direction });
}

export function sendPlaybackRate(roomCode: string, rate: string): boolean {
  return publishSignal(`/app/room/${roomCode}/playback/rate`, { rate });
}

export function sendPlaybackLoop(
  roomCode: string,
  mode: "OFF" | "ONE",
): boolean {
  return publishSignal(`/app/room/${roomCode}/playback/loop`, { mode });
}

export function sendPlaybackShuffle(
  roomCode: string,
  enabled: boolean,
): boolean {
  return publishSignal(`/app/room/${roomCode}/playback/shuffle`, { enabled });
}

export function disconnectStompClient(): void {
  for (const tracked of trackedSubs) {
    if (tracked.subscription) {
      tracked.subscription.unsubscribe();
      tracked.subscription = null;
    }
  }
  trackedSubs.clear();
  if (sharedClient) {
    sharedClient.deactivate();
    sharedClient = null;
    currentToken = null;
  }
}
