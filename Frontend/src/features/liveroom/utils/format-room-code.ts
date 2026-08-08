import { ROOM_CODE_LENGTH } from "../types";

const ALLOWED = /[^A-Z0-9]/g;

export function normalizeRoomCode(raw: string): string {
  return raw.toUpperCase().replace(ALLOWED, "").slice(0, ROOM_CODE_LENGTH);
}

export function isCompleteRoomCode(code: string): boolean {
  return normalizeRoomCode(code).length === ROOM_CODE_LENGTH;
}

export function formatRoomCode(code: string): string {
  const normalized = normalizeRoomCode(code);
  if (normalized.length <= 3) return normalized;
  return `${normalized.slice(0, 3)} ${normalized.slice(3)}`;
}