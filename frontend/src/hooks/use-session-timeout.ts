"use client";

import { useEffect, useRef, useState, useCallback } from "react";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";
import { refreshAccessToken, abortRefresh } from "@/lib/auth-refresh";

const PROACTIVE_REFRESH_MS = 5 * 60 * 1000;
const COUNTDOWN_INTERVAL_MS = 1000;
const MAX_RETRY_ATTEMPTS = 3;
const RETRY_DELAY_MS = 2000;

interface UseSessionTimeoutOptions {
  onRefreshSuccess?: () => void;
  onSessionExpired?: () => void;
}

export interface SessionTimeoutState {
  remainingMs: number;
  isRefreshing: boolean;
  shouldShowExpired: boolean;
  refreshFailed: boolean;
}

interface InternalState {
  expiresAt: number | null;
  hasAuth: boolean;
  isRefreshing: boolean;
  retryCount: number;
  refreshKey: string | null;
  retryTimeoutId: ReturnType<typeof setTimeout> | null;
}

const EMPTY_INTERNAL: InternalState = {
  expiresAt: null,
  hasAuth: false,
  isRefreshing: false,
  retryCount: 0,
  refreshKey: null,
  retryTimeoutId: null,
};

export function useSessionTimeout(options: UseSessionTimeoutOptions = {}): SessionTimeoutState {
  const [remainingMs, setRemainingMs] = useState<number>(0);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [shouldShowExpired, setShouldShowExpired] = useState(false);
  const [refreshFailed, setRefreshFailed] = useState(false);

  const stateRef = useRef<InternalState>({ ...EMPTY_INTERNAL });
  const optionsRef = useRef(options);
  optionsRef.current = options;

  const clearRetryTimeout = useCallback(() => {
    if (stateRef.current.retryTimeoutId) {
      clearTimeout(stateRef.current.retryTimeoutId);
      stateRef.current.retryTimeoutId = null;
    }
  }, []);

  const handleRefreshSuccess = useCallback(() => {
    stateRef.current.retryCount = 0;
    setRefreshFailed(false);
    setShouldShowExpired(false);
    optionsRef.current.onRefreshSuccess?.();
  }, []);

  const handleSessionExpired = useCallback(() => {
    abortRefresh();
    useAuthStore.getState().clearAuth();
    setShouldShowExpired(true);
    optionsRef.current.onSessionExpired?.();
  }, []);

  const attemptRefresh = useCallback(async (): Promise<void> => {
    if (stateRef.current.isRefreshing) return;
    stateRef.current.isRefreshing = true;
    setIsRefreshing(true);

    try {
      await refreshAccessToken();
      handleRefreshSuccess();
    } catch {
      stateRef.current.retryCount += 1;
      if (stateRef.current.retryCount < MAX_RETRY_ATTEMPTS) {
        clearRetryTimeout();
        stateRef.current.retryTimeoutId = setTimeout(() => {
          void attemptRefresh();
        }, RETRY_DELAY_MS);
      } else {
        setRefreshFailed(true);
        handleSessionExpired();
      }
    } finally {
      stateRef.current.isRefreshing = false;
      setIsRefreshing(false);
    }
  }, [handleRefreshSuccess, handleSessionExpired, clearRetryTimeout]);

  useEffect(() => {
    const handleAuthChange = () => {
      const authState = useAuthStore.getState();
      const { accessToken, accessTokenExpiresAt, user } = authState;

      const hasAuth = !!accessToken && !!accessTokenExpiresAt && !!user;
      const prevHasAuth = stateRef.current.hasAuth;
      stateRef.current.hasAuth = hasAuth;
      stateRef.current.expiresAt = accessTokenExpiresAt ?? null;

      if (!hasAuth) {
        if (prevHasAuth) {
          clearRetryTimeout();
          stateRef.current.retryCount = 0;
          stateRef.current.refreshKey = null;
          setRemainingMs(0);
          setIsRefreshing(false);
          setShouldShowExpired(false);
          setRefreshFailed(false);
        }
        return;
      }

      const remaining = (accessTokenExpiresAt ?? 0) - Date.now();
      setRemainingMs(remaining > 0 ? remaining : 0);

      const refreshKey = `${accessToken}-${accessTokenExpiresAt}`;
      const keyChanged = stateRef.current.refreshKey !== refreshKey;
      if (keyChanged) {
        stateRef.current.refreshKey = refreshKey;
        stateRef.current.retryCount = 0;
      }

      if (remaining > PROACTIVE_REFRESH_MS) {
        return;
      }

      if (remaining <= PROACTIVE_REFRESH_MS && remaining > 0) {
        if (keyChanged && !stateRef.current.isRefreshing) {
          void attemptRefresh();
        }
      }
    };

    handleAuthChange();
    const unsubscribe = useAuthStore.subscribe(handleAuthChange);
    return () => {
      unsubscribe();
      clearRetryTimeout();
    };
  }, [attemptRefresh, clearRetryTimeout]);

  useEffect(() => {
    const interval = setInterval(() => {
      const expiresAt = stateRef.current.expiresAt;
      if (!expiresAt || !stateRef.current.hasAuth) return;
      const remaining = expiresAt - Date.now();
      setRemainingMs((prev) => {
        const next = remaining > 0 ? remaining : 0;
        return prev === next ? prev : next;
      });
    }, COUNTDOWN_INTERVAL_MS);

    return () => clearInterval(interval);
  }, []);

  return { remainingMs, isRefreshing, shouldShowExpired, refreshFailed };
}

export function formatRemainingTime(ms: number): string {
  if (ms <= 0) return "0:00";
  const totalSeconds = Math.floor(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, "0")}`;
}
