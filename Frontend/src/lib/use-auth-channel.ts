"use client";

import { useEffect, useMemo } from "react";
import { AUTH_CHANNEL, broadcastAuthMessage, type AuthChannelMessage } from "./broadcast-channel";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";
import { abortRefresh } from "./auth-refresh";

type Listener = (msg: AuthChannelMessage) => void;

let channelInstance: BroadcastChannel | null = null;
const listeners = new Set<Listener>();

function getOrCreateChannel(): BroadcastChannel | null {
  if (typeof window === "undefined" || typeof BroadcastChannel === "undefined") return null;
  if (channelInstance) return channelInstance;

  const ch = new BroadcastChannel(AUTH_CHANNEL);
  ch.onmessage = (event) => {
    const msg = event.data as AuthChannelMessage;
    listeners.forEach((cb) => {
      try {
        cb(msg);
      } catch {
        // ignore listener errors so a buggy subscriber can't break peers
      }
    });
  };
  channelInstance = ch;
  return ch;
}

export function addAuthChannelListener(listener: Listener): () => void {
  getOrCreateChannel();
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function removeAuthChannelListener(listener: Listener): void {
  listeners.delete(listener);
}

/**
 * Subscribe to cross-tab auth sync for the lifetime of a component.
 *
 * Why not a bare singleton: a previous version used a `handledRef` flag to
 * guard `addAuthChannelListener` against double-registration, but the
 * `useEffect` cleanup still called `removeAuthChannelListener` every
 * unmount - so remounting under Strict Mode (or after a layout swap)
 * would re-register the same handler. Worse: if two pages that both
 * called this hook unmounted in arbitrary order, the handler that was
 * "registered last" could be the one that gets removed.
 *
 * The new version returns an `unsubscribe` from `addAuthChannelListener`
 * and we trust React to call it exactly once per mount/unmount. No more
 * ref-guarded singleton - the BroadcastChannel itself is the singleton.
 */
export function useAuthChannelSync(): void {
  const handler = useMemo(
    () => (msg: AuthChannelMessage) => {
      if (msg.type === "LOGOUT") {
        abortRefresh();
        useAuthStore.getState().clearAuth();
      } else if (msg.type === "TOKEN_UPDATED") {
        if (msg.token && msg.user) {
          useAuthStore.getState().setAuth(msg.token, msg.user);
        }
      }
    },
    [],
  );

  useEffect(() => {
    const unsubscribe = addAuthChannelListener(handler);
    return () => {
      unsubscribe();
    };
  }, [handler]);
}

export { broadcastAuthMessage };
