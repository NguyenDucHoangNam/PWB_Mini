import type { RoomLookup } from "../types";

const IDEM_KEY_PREFIX = "liveroom:idemKey:";
const KICKED_PREFIX = "liveroom:kicked:";
const LOOKUP_PREFIX = "liveroom:lookup:";
const MEDIA_INTENT_PREFIX = "liveroom:mediaIntent:";

const IDEM_KEY_TTL_MS = 24 * 60 * 60 * 1000;
const KICKED_TTL_MS = 60 * 60 * 1000;
const LOOKUP_TTL_MS = 60 * 60 * 1000;
const MEDIA_INTENT_TTL_MS = 6 * 60 * 60 * 1000;

interface Envelope<T> {
  value: T;
  savedAt: number;
}

function readStore(session: boolean): Storage | null {
  if (typeof window === "undefined") return null;
  try {
    return session ? window.sessionStorage : window.localStorage;
  } catch {
    return null;
  }
}

function read<T>(key: string, ttlMs: number, session = false): T | null {
  const store = readStore(session);
  if (!store) return null;
  try {
    const raw = store.getItem(key);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Envelope<T>;
    if (!parsed || typeof parsed.savedAt !== "number") return null;
    if (Date.now() - parsed.savedAt > ttlMs) {
      store.removeItem(key);
      return null;
    }
    return parsed.value;
  } catch {
    return null;
  }
}

function write<T>(key: string, value: T, session = false): void {
  const store = readStore(session);
  if (!store) return;
  try {
    store.setItem(key, JSON.stringify({ value, savedAt: Date.now() } satisfies Envelope<T>));
  } catch {

  }
}

function remove(key: string, session = false): void {
  const store = readStore(session);
  if (!store) return;
  try {
    store.removeItem(key);
  } catch {

  }
}


export function getOrCreateIdempotencyKey(roomId: string): string {
  const existing = read<string>(IDEM_KEY_PREFIX + roomId, IDEM_KEY_TTL_MS);
  if (existing) return existing;
  const fresh =
    typeof crypto !== "undefined" && "randomUUID" in crypto
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  write(IDEM_KEY_PREFIX + roomId, fresh);
  return fresh;
}

export function clearIdempotencyKey(roomId: string): void {
  remove(IDEM_KEY_PREFIX + roomId);
}

export interface KickedRecord {
  kickedAt: string;
  cooldownUntil: string | null;
  reason: string | null;
}

export function readKicked(roomId: string): KickedRecord | null {
  return read<KickedRecord>(KICKED_PREFIX + roomId, KICKED_TTL_MS);
}

export function writeKicked(roomId: string, record: KickedRecord): void {
  write(KICKED_PREFIX + roomId, record);
}

export function clearKicked(roomId: string): void {
  remove(KICKED_PREFIX + roomId);
}


export interface MediaIntent {
  cameraOn: boolean;
  micOn: boolean;
}

export function readMediaIntent(roomId: string): MediaIntent | null {
  return read<MediaIntent>(MEDIA_INTENT_PREFIX + roomId, MEDIA_INTENT_TTL_MS, true);
}

export function writeMediaIntent(roomId: string, intent: MediaIntent): void {
  write(MEDIA_INTENT_PREFIX + roomId, intent, true);
}

export function clearMediaIntent(roomId: string): void {
  remove(MEDIA_INTENT_PREFIX + roomId, true);
}


export function readLookup(roomCode: string): RoomLookup | null {
  return read<RoomLookup>(LOOKUP_PREFIX + roomCode, LOOKUP_TTL_MS, true);
}

export function writeLookup(roomCode: string, lookup: RoomLookup): void {
  write(LOOKUP_PREFIX + roomCode, lookup, true);
}

export function clearLookup(roomCode: string): void {
  remove(LOOKUP_PREFIX + roomCode, true);
}