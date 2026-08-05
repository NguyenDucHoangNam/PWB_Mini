let offsetMs = 0;
let synced = false;

export function syncServerClock(serverIsoTime: string | null | undefined): void {
  if (!serverIsoTime) return;
  const serverMs = Date.parse(serverIsoTime);
  if (Number.isNaN(serverMs)) return;
  offsetMs = serverMs - Date.now();
  synced = true;
}

export function serverNow(): number {
  return Date.now() + offsetMs;
}

export function isServerClockSynced(): boolean {
  return synced;
}

export function serverClockOffsetMs(): number {
  return offsetMs;
}

export function resetServerClock(): void {
  offsetMs = 0;
  synced = false;
}

export function msUntil(deadlineIso: string | null | undefined): number {
  if (!deadlineIso) return 0;
  const deadlineMs = Date.parse(deadlineIso);
  if (Number.isNaN(deadlineMs)) return 0;
  return Math.max(0, deadlineMs - serverNow());
}

export function secondsUntil(deadlineIso: string | null | undefined): number {
  return Math.ceil(msUntil(deadlineIso) / 1000);
}

export function hasPassed(deadlineIso: string | null | undefined): boolean {
  if (!deadlineIso) return true;
  return msUntil(deadlineIso) <= 0;
}