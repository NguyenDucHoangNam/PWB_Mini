"use client";

import { useEffect, useRef, useState, useCallback } from "react";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";
import { refreshAccessToken, abortRefresh } from "@/lib/auth-refresh";

const WARNING_THRESHOLD_MS = 60 * 1000; // warn when 60s remain before expiry
const CHECK_INTERVAL_MS = 5 * 1000;

interface UseSessionTimeoutOptions {
  /**
   * Called when the user has chosen to extend the session (the "Continue"
   * button in the warning dialog). The implementation should attempt a
   * refresh and reset the warning state.
   */
  onExtend?: () => Promise<void> | void;
  /**
   * Called when the user has chosen to log out immediately. The
   * implementation should clear auth state and redirect to /login.
   */
  onLogout?: () => void;
}

export interface SessionTimeoutState {
  showWarning: boolean;
  remainingMs: number;
  extend: () => Promise<void>;
  logout: () => void;
}

/**
 * Detects when the JWT access token is about to expire and surfaces a
 * warning UI before the user is silently logged out.
 *
 * The warning threshold is driven by the real JWT `exp` claim
 * (decoded on token set) rather than a separate inactivity timer.
 *
 * Continue: refreshes the token via the silent refresh endpoint.
 * Logout: clears auth state via the supplied `onLogout` callback.
 */
export function useSessionTimeout(options: UseSessionTimeoutOptions = {}): SessionTimeoutState {
  const accessToken = useAuthStore((s) => s.accessToken);
  const accessTokenExpiresAt = useAuthStore((s) => s.accessTokenExpiresAt);
  const user = useAuthStore((s) => s.user);
  const [showWarning, setShowWarning] = useState(false);
  const [remainingMs, setRemainingMs] = useState<number>(0);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const isRefreshingRef = useRef(false);

  const evaluate = useCallback(() => {
    if (!accessToken || !accessTokenExpiresAt || !user) {
      // setState only when actually changing - prevents a re-render every
      // 5 seconds for users who simply aren't logged in.
      setShowWarning((prev) => (prev ? false : prev));
      setRemainingMs((prev) => (prev === 0 ? prev : 0));
      return;
    }
    const remaining = Math.max(0, accessTokenExpiresAt - Date.now());
    const shouldShow = remaining > 0 && remaining <= WARNING_THRESHOLD_MS;

    // Conditional setters avoid triggering re-renders when nothing has
    // changed (e.g. the user is on a long-lived stable session).
    setRemainingMs((prev) => (prev === remaining ? prev : remaining));
    setShowWarning((prev) => (prev === shouldShow ? prev : shouldShow));
  }, [accessToken, accessTokenExpiresAt, user]);

  useEffect(() => {
    // evaluate() reads from the auth store and derives `remaining`/
    // `shouldShow`. Calling it on mount seeds the timer; the setState
    // calls inside are guarded by `prev === next` checks so they produce
    // no re-render when nothing has actually changed.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    evaluate();
    intervalRef.current = setInterval(evaluate, CHECK_INTERVAL_MS);
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
  }, [evaluate]);

  const extend = useCallback(async () => {
    if (isRefreshingRef.current) return;
    isRefreshingRef.current = true;
    try {
      if (options.onExtend) {
        await options.onExtend();
      } else {
        await refreshAccessToken();
      }
      // The store update via refreshAccessToken() will trigger evaluate().
      setShowWarning(false);
    } catch {
      // Refresh failed - the api-client interceptor will redirect to /login.
      setShowWarning(false);
    } finally {
      isRefreshingRef.current = false;
    }
  }, [options]);

  const logout = useCallback(() => {
    if (options.onLogout) {
      options.onLogout();
      return;
    }
    abortRefresh();
    useAuthStore.getState().clearAuth();
    if (typeof window !== "undefined") {
      window.location.href = "/login";
    }
  }, [options]);

  return { showWarning, remainingMs, extend, logout };
}

export function formatRemainingTime(ms: number): string {
  if (ms <= 0) return "0:00";
  const totalSeconds = Math.floor(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, "0")}`;
}
