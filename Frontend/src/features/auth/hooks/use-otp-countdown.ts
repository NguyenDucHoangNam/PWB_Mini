"use client";

import { useCallback, useEffect, useState } from "react";

function computeRemaining(ttlSeconds: number, serverTimestamp?: number | null): number {
  if (!serverTimestamp) return Math.max(0, Math.floor(ttlSeconds));
  const elapsedMs = Date.now() - serverTimestamp;
  const remainingMs = ttlSeconds * 1000 - elapsedMs;
  return Math.max(0, Math.floor(remainingMs / 1000));
}

interface UseExpiryCountdownOptions {
  ttlSeconds: number;
  serverTimestamp?: number | null;
}

export function useExpiryCountdown({ ttlSeconds, serverTimestamp }: UseExpiryCountdownOptions) {
  const [remaining, setRemaining] = useState<number>(() =>
    computeRemaining(ttlSeconds, serverTimestamp),
  );

  useEffect(() => {
    const recompute = () => setRemaining(computeRemaining(ttlSeconds, serverTimestamp));
    recompute();
  }, [ttlSeconds, serverTimestamp]);

  useEffect(() => {
    if (remaining <= 0) return;
    const interval = setInterval(() => {
      setRemaining((prev) => (prev > 0 ? prev - 1 : 0));
    }, 1000);
    return () => clearInterval(interval);
  }, [remaining]);

  const sync = useCallback(() => {
    setRemaining(computeRemaining(ttlSeconds, serverTimestamp));
  }, [ttlSeconds, serverTimestamp]);

  useEffect(() => {
    const handler = () => sync();
    document.addEventListener("visibilitychange", handler);
    window.addEventListener("focus", handler);
    return () => {
      document.removeEventListener("visibilitychange", handler);
      window.removeEventListener("focus", handler);
    };
  }, [sync]);

  return { remaining, sync };
}

interface UseCooldownOptions {
  ttlSeconds: number;
}

interface CooldownState {
  remaining: number;
  reset: () => void;
  setFromServer: (serverTimestamp: number | null, overrideTtl?: number) => void;
}

export function useCooldown({ ttlSeconds }: UseCooldownOptions): CooldownState {
  const [expiresAt, setExpiresAt] = useState<number | null>(null);
  const [now, setNow] = useState<number>(() => Date.now());

  useEffect(() => {
    if (expiresAt === null) return;
    const interval = setInterval(() => {
      const current = Date.now();
      setNow(current);
      if (current >= expiresAt) {
        setExpiresAt(null);
      }
    }, 1000);
    return () => clearInterval(interval);
  }, [expiresAt]);

  const remainingMs = expiresAt ? expiresAt - now : 0;
  const remaining = Math.max(0, Math.ceil(remainingMs / 1000));

  const reset = useCallback(() => {
    const current = Date.now();
    setNow(current);
    setExpiresAt(current + ttlSeconds * 1000);
  }, [ttlSeconds]);

  const setFromServer = useCallback((serverTimestamp: number | null, overrideTtl?: number) => {
    const ttl = overrideTtl ?? ttlSeconds;
    if (!serverTimestamp) {
      const current = Date.now();
      setNow(current);
      setExpiresAt(current + ttl * 1000);
      return;
    }
    const elapsed = Date.now() - serverTimestamp;
    const remainingServer = ttl * 1000 - elapsed;
    const current = Date.now();
    setNow(current);
    setExpiresAt(current + Math.max(0, remainingServer));
  }, [ttlSeconds]);

  return { remaining, reset, setFromServer };
}