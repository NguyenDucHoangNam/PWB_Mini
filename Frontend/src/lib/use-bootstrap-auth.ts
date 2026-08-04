"use client";

import { useEffect, useRef } from "react";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";
import { refreshAccessToken } from "./auth-refresh";


const BOOTSTRAP_TIMEOUT_MS = 8000;

/**
 * Restore the session from the httpOnly refresh cookie on first mount.
 *
 * Without this, a hard refresh or new-tab open lands on a protected route,
 * the in-memory Zustand store is empty, the route guard sees no token, and
 * the user gets bounced to /login even though the refresh cookie is still
 * valid.
 *
 * The refresh cookie is the only persistent credential carrier. We exchange
 * it for an access token here; on success the store is hydrated and the
 * rest of the app renders as if the user had just logged in.
 *
 * The hook is idempotent: `bootstrappingRef` ensures concurrent mounts
 * (e.g. Strict Mode double-invoke) only fire one refresh request. Once
 * `status` flips to `ready` or `failed` it stays put for the lifetime of
 * the tab; subsequent logout/login flows handle their own state.
 */
export function useBootstrapAuth(): void {
  const setBootstrapping = useAuthStore((state) => state.setBootstrapping);
  const bootstrappingRef = useRef(false);

  useEffect(() => {
    if (useAuthStore.getState().accessToken) {
      setBootstrapping(false);
      return;
    }
    if (bootstrappingRef.current) return;

    bootstrappingRef.current = true;

    const timeoutId = window.setTimeout(() => {
      if (useAuthStore.getState().accessToken) return;
      setBootstrapping(false);
    }, BOOTSTRAP_TIMEOUT_MS);

    refreshAccessToken()
      .then(() => {
        window.clearTimeout(timeoutId);
        setBootstrapping(false);
      })
      .catch(() => {
        window.clearTimeout(timeoutId);
        setBootstrapping(false);
      });
  }, [setBootstrapping]);
}