import { Client, type IMessage, type StompSubscription } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { API_BASE_URL } from "@/lib/constants";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";
import type { ParticipantWsEvent } from "../types";

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

export function getStompClient(accessToken: string): Client {
  if (sharedClient && currentToken === accessToken) {
    return sharedClient;
  }

  if (sharedClient) {
    sharedClient.deactivate();
    sharedClient = null;
  }

  sharedClient = new Client({
    webSocketFactory: () => new SockJS(WS_URL) as unknown as WebSocket,
    connectHeaders: {
      Authorization: `Bearer ${accessToken}`,
    },
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    debug: () => {},
  });
  currentToken = accessToken;
  sharedClient.activate();
  return sharedClient;
}

function ensureClient(): Client | null {
  const token = useAuthStore.getState().accessToken;
  if (!token) return null;
  const client = getStompClient(token);
  if (!client.connected) {
    client.activate();
  }
  return client;
}

export interface ParticipantSubscription {
  unsubscribe: () => void;
}

export function subscribeRoomParticipants(
  roomCode: string,
  onEvent: (event: ParticipantWsEvent) => void,
): ParticipantSubscription {
  const client = ensureClient();
  if (!client) {
    return { unsubscribe: () => {} };
  }

  let subscription: StompSubscription | null = null;
  let activated = false;

  const handleFrame = (frame: IMessage) => {
    try {
      const payload = JSON.parse(frame.body) as ParticipantWsEvent;
      onEvent(payload);
    } catch {
      /* ignore malformed frames */
    }
  };

  const attach = () => {
    if (subscription) return;
    subscription = client.subscribe(`/topic/room/${roomCode}/participants`, handleFrame);
  };

  if (client.connected) {
    attach();
  } else {
    const originalOnConnect = client.onConnect;
    client.onConnect = (frame) => {
      originalOnConnect?.(frame);
      attach();
    };
  }

  activated = true;

  return {
    unsubscribe: () => {
      if (subscription) {
        subscription.unsubscribe();
        subscription = null;
      }
      if (activated) {
        activated = false;
      }
    },
  };
}

export function disconnectStompClient(): void {
  if (sharedClient) {
    sharedClient.deactivate();
    sharedClient = null;
    currentToken = null;
  }
}
