export const LIVEROOM_CHANNEL = "pwb_liveroom_channel";

const CLAIM_PREFIX = "liveroom:tab:";
const HEARTBEAT_MS = 5000;
const STALE_AFTER_MS = 15_000;
const PROBE_WAIT_MS = 600;

export type LiveroomTabMessage =
  | { type: "PING"; roomId: string; tabId: string }
  | { type: "PONG"; roomId: string; tabId: string }
  | { type: "CLAIM"; roomId: string; tabId: string }
  | { type: "RELEASE"; roomId: string; tabId: string }
  | { type: "FOCUS_REQUEST"; roomId: string; tabId: string };

type Listener = (message: LiveroomTabMessage) => void;

let channel: BroadcastChannel | null = null;
const listeners = new Set<Listener>();

function getChannel(): BroadcastChannel | null {
  if (typeof window === "undefined" || typeof BroadcastChannel === "undefined") return null;
  if (channel) return channel;
  const created = new BroadcastChannel(LIVEROOM_CHANNEL);
  created.onmessage = (event) => {
    const message = event.data as LiveroomTabMessage;
    listeners.forEach((listener) => {
      try {
        listener(message);
      } catch {

      }
    });
  };
  channel = created;
  return created;
}

export function addTabListener(listener: Listener): () => void {
  getChannel();
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function postTabMessage(message: LiveroomTabMessage): void {
  getChannel()?.postMessage(message);
}

interface Claim {
  tabId: string;
  heartbeatAt: number;
}

function claimKey(roomId: string): string {
  return CLAIM_PREFIX + roomId;
}

export function readClaim(roomId: string): Claim | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = window.localStorage.getItem(claimKey(roomId));
    if (!raw) return null;
    return JSON.parse(raw) as Claim;
  } catch {
    return null;
  }
}

export function writeClaim(roomId: string, tabId: string): void {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(
      claimKey(roomId),
      JSON.stringify({ tabId, heartbeatAt: Date.now() } satisfies Claim),
    );
  } catch {

  }
}

export function releaseClaim(roomId: string, tabId: string): void {
  if (typeof window === "undefined") return;
  const current = readClaim(roomId);
  if (current && current.tabId !== tabId) return;
  try {
    window.localStorage.removeItem(claimKey(roomId));
  } catch {

  }
}


export function isClaimStale(claim: Claim | null): boolean {
  if (!claim) return true;
  return Date.now() - claim.heartbeatAt > STALE_AFTER_MS;
}

export const TAB_LOCK_TIMINGS = {
  heartbeatMs: HEARTBEAT_MS,
  probeWaitMs: PROBE_WAIT_MS,
  staleAfterMs: STALE_AFTER_MS,
};

export function newTabId(): string {
  return typeof crypto !== "undefined" && "randomUUID" in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}