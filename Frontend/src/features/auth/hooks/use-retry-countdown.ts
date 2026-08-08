"use client";

import { useCallback, useEffect, useState } from "react";

interface UseRetryCountdownOptions {
  defaultSeconds?: number;
}

interface UseRetryCountdownReturn {
  remaining: number;
  isActive: boolean;
  start: (seconds: number) => void;
  clear: () => void;
  startFromError: (retryAfterSeconds: number | undefined | null, fallbackSeconds?: number) => boolean;
}

const FALLBACK_SECONDS = 60;

export function useRetryCountdown({
  defaultSeconds = FALLBACK_SECONDS,
}: UseRetryCountdownOptions = {}): UseRetryCountdownReturn {
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

  const start = useCallback((seconds: number) => {
    const safeSeconds = Number.isFinite(seconds) && seconds > 0 ? seconds : defaultSeconds;
    const current = Date.now();
    setNow(current);
    setExpiresAt(current + safeSeconds * 1000);
  }, [defaultSeconds]);

  const clear = useCallback(() => {
    setExpiresAt(null);
  }, []);

  const startFromError = useCallback(
    (retryAfterSeconds: number | undefined | null, fallbackSeconds?: number) => {
      if (typeof retryAfterSeconds === "number" && retryAfterSeconds > 0) {
        start(retryAfterSeconds);
        return true;
      }
      if (fallbackSeconds && fallbackSeconds > 0) {
        start(fallbackSeconds);
        return true;
      }
      return false;
    },
    [start],
  );

  return {
    remaining,
    isActive: remaining > 0,
    start,
    clear,
    startFromError,
  };
}