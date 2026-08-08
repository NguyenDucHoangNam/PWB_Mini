import { Client, type IMessage, type StompSubscription } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";
import { refreshAccessToken } from "@/lib/auth-refresh";
import type { ApiResponse } from "@/types/api";
import type { LiveroomEvent } from "../types/events";
import {
  LIVEROOM_WS_HTTP_URL,
  LIVEROOM_WS_URL,
  PERSONAL_ERROR_QUEUE,
  PERSONAL_QUEUE,
  PERSONAL_RTC_QUEUE,
  roomChatTopic,
  roomMusicTopic,
  roomTopic,
} from "./liveroom-destinations";
import { syncServerClock } from "./server-clock";

export type ConnectionStatus =
  | "idle"
  | "connecting"
  | "connected"
  | "reconnecting"
  | "offline"
  | "unauthorized";

type EventListener = (event: LiveroomEvent) => void;
type FrameErrorListener = (error: ApiResponse<unknown>) => void;
type StatusListener = (status: ConnectionStatus, attempt: number) => void;

const MAX_ATTEMPTS = 8;
const SOCKJS_AFTER_FAILURES = 2;
const TOKEN_REFRESH_MARGIN_MS = 30_000;


async function freshToken(): Promise<string> {
  const { accessToken, accessTokenExpiresAt } = useAuthStore.getState();
  const expiringSoon =
    !accessTokenExpiresAt || accessTokenExpiresAt - Date.now() < TOKEN_REFRESH_MARGIN_MS;
  if (!accessToken || expiringSoon) return refreshAccessToken();
  return accessToken;
}

function backoffDelay(attempt: number): number {
  return Math.min(15_000, 1000 * 2 ** Math.max(0, attempt - 1)) + Math.random() * 400;
}

class LiveroomSocket {
  private client: Client | null = null;
  private status: ConnectionStatus = "idle";
  private attempt = 0;
  private closesWithoutConnect = 0;
  private useSockJs = false;
  private reconnectTimer: number | null = null;
  private wantConnected = false;

  private roomId: string | null = null;
  private roomAllowed = false;

  private personalSubs: StompSubscription[] = [];
  private roomSubs: StompSubscription[] = [];

  private eventListeners = new Set<EventListener>();
  private frameErrorListeners = new Set<FrameErrorListener>();
  private statusListeners = new Set<StatusListener>();

  getStatus(): ConnectionStatus {
    return this.status;
  }

  onEvent(listener: EventListener): () => void {
    this.eventListeners.add(listener);
    return () => this.eventListeners.delete(listener);
  }

  onFrameError(listener: FrameErrorListener): () => void {
    this.frameErrorListeners.add(listener);
    return () => this.frameErrorListeners.delete(listener);
  }

  onStatus(listener: StatusListener): () => void {
    this.statusListeners.add(listener);
    listener(this.status, this.attempt);
    return () => this.statusListeners.delete(listener);
  }

  connect(): void {
    this.wantConnected = true;
    if (this.client?.active) {
      this.setStatus(this.status, this.attempt);
      return;
    }
    this.openClient();
  }

  disconnect(): void {
    this.wantConnected = false;
    this.clearReconnectTimer();
    this.roomId = null;
    this.roomAllowed = false;
    this.personalSubs = [];
    this.roomSubs = [];
    const client = this.client;
    this.client = null;
    this.setStatus("idle", 0);
    void client?.deactivate();
  }

  retry(): void {
    this.attempt = 0;
    this.closesWithoutConnect = 0;
    this.useSockJs = false;
    this.connect();
  }


  ensureRoomSubscriptions(roomId: string, allowed: boolean): void {
    const changed = this.roomId !== roomId || this.roomAllowed !== allowed;
    this.roomId = roomId;
    this.roomAllowed = allowed;
    if (!changed) return;

    this.unsubscribeRoom();
    if (allowed) this.subscribeRoom();
  }

  publish(destination: string, body?: unknown): void {
    if (!this.client?.connected) return;
    this.client.publish({
      destination,
      body: body === undefined ? "{}" : JSON.stringify(body),
    });
  }

  private openClient(): void {
    this.clearReconnectTimer();
    const client = new Client({
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      reconnectDelay: 0,
      debug: () => {},
    });

    if (this.useSockJs) {
      client.webSocketFactory = () => SockJS(LIVEROOM_WS_HTTP_URL) as unknown as WebSocket;
    } else {
      client.brokerURL = LIVEROOM_WS_URL;
    }

    client.beforeConnect = async () => {
      const token = await freshToken();
      client.connectHeaders = { Authorization: `Bearer ${token}` };
    };

    client.onConnect = () => {
      this.attempt = 0;
      this.closesWithoutConnect = 0;
      this.subscribePersonal();
      if (this.roomId && this.roomAllowed) this.subscribeRoom();
      this.setStatus("connected", 0);
    };

    client.onStompError = (frame) => {
      this.setStatus("unauthorized", this.attempt);
      this.wantConnected = false;
      this.emitFrameError({
        success: false,
        message: frame.headers.message ?? "",
        data: null,
        errors: null,
        code: "LR_080",
        timestamp: new Date().toISOString(),
      });
    };

    client.onWebSocketClose = () => {
      this.personalSubs = [];
      this.roomSubs = [];
      if (!this.wantConnected) return;
      this.closesWithoutConnect += 1;
      if (!this.useSockJs && this.closesWithoutConnect >= SOCKJS_AFTER_FAILURES) {
        this.useSockJs = true;
      }
      this.scheduleReconnect();
    };

    this.client = client;
    this.setStatus(this.attempt === 0 ? "connecting" : "reconnecting", this.attempt);
    client.activate();
  }

  private scheduleReconnect(): void {
    this.attempt += 1;
    if (this.attempt > MAX_ATTEMPTS) {
      this.setStatus("offline", this.attempt);
      this.wantConnected = false;
      return;
    }
    this.setStatus("reconnecting", this.attempt);

    const delay = backoffDelay(this.attempt);
    const start = () => {
      if (!this.wantConnected) return;
      if (typeof navigator !== "undefined" && navigator.onLine === false) {
        this.reconnectTimer = window.setTimeout(start, 2000);
        return;
      }
      void this.client?.deactivate();
      this.openClient();
    };
    this.reconnectTimer = window.setTimeout(start, delay);
  }

  private clearReconnectTimer(): void {
    if (this.reconnectTimer !== null) {
      window.clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  private subscribePersonal(): void {
    const client = this.client;
    if (!client?.connected || this.personalSubs.length > 0) return;

    this.personalSubs = [
      client.subscribe(PERSONAL_ERROR_QUEUE, (message) => this.handleFrameError(message)),
      client.subscribe(PERSONAL_QUEUE, (message) => this.handleEvent(message)),
      client.subscribe(PERSONAL_RTC_QUEUE, (message) => this.handleEvent(message)),
    ];
  }

  private subscribeRoom(): void {
    const client = this.client;
    if (!client?.connected || !this.roomId || this.roomSubs.length > 0) return;

    this.roomSubs = [
      client.subscribe(roomTopic(this.roomId), (message) => this.handleEvent(message)),
      client.subscribe(roomChatTopic(this.roomId), (message) => this.handleEvent(message)),
      client.subscribe(roomMusicTopic(this.roomId), (message) => this.handleEvent(message)),
    ];
  }

  private unsubscribeRoom(): void {
    this.roomSubs.forEach((sub) => {
      try {
        sub.unsubscribe();
      } catch {

      }
    });
    this.roomSubs = [];
  }

  private handleEvent(message: IMessage): void {
    let parsed: LiveroomEvent;
    try {
      parsed = JSON.parse(message.body) as LiveroomEvent;
    } catch {
      return;
    }
    syncServerClock(parsed.timestamp);
    this.eventListeners.forEach((listener) => {
      try {
        listener(parsed);
      } catch {

      }
    });
  }

  private handleFrameError(message: IMessage): void {
    try {
      this.emitFrameError(JSON.parse(message.body) as ApiResponse<unknown>);
    } catch {

    }
  }

  private emitFrameError(error: ApiResponse<unknown>): void {
    this.frameErrorListeners.forEach((listener) => {
      try {
        listener(error);
      } catch {

      }
    });
  }

  private setStatus(status: ConnectionStatus, attempt: number): void {
    this.status = status;
    this.attempt = attempt;
    this.statusListeners.forEach((listener) => {
      try {
        listener(status, attempt);
      } catch {

      }
    });
  }
}

export const liveroomSocket = new LiveroomSocket();