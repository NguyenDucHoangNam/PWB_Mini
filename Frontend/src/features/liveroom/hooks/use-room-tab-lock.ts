"use client";

import { useCallback, useEffect, useState } from "react";
import {
  TAB_LOCK_TIMINGS,
  addTabListener,
  isClaimStale,
  newTabId,
  postTabMessage,
  readClaim,
  releaseClaim,
  writeClaim,
} from "../lib/room-tab-lock";

export type TabLockState = "probing" | "owner" | "conflict";

export function useRoomTabLock(roomId: string): {
  state: TabLockState;
  requestFocusOnOwner: () => void;
  takeOver: () => void;
} {
  const [state, setState] = useState<TabLockState>("probing");
  const [tabId] = useState(newTabId);

  const claim = useCallback(() => {
    writeClaim(roomId, tabId);
    postTabMessage({ type: "CLAIM", roomId, tabId });
    setState("owner");
  }, [roomId, tabId]);

  useEffect(() => {
    if (!roomId) return;
    let settled = false;

    const unsubscribe = addTabListener((message) => {
      if (message.roomId !== roomId || message.tabId === tabId) return;
      if (message.type === "PING") {
        postTabMessage({ type: "PONG", roomId, tabId });
        return;
      }
      if ((message.type === "PONG" || message.type === "CLAIM") && !settled) {
        settled = true;
        setState("conflict");
        return;
      }
      if (message.type === "FOCUS_REQUEST") window.focus();
    });

    postTabMessage({ type: "PING", roomId, tabId });

    const decide = window.setTimeout(() => {
      if (settled) return;
      settled = true;
      const existing = readClaim(roomId);
      if (existing && existing.tabId !== tabId && !isClaimStale(existing)) {
        setState("conflict");
        return;
      }
      claim();
    }, TAB_LOCK_TIMINGS.probeWaitMs);

    return () => {
      window.clearTimeout(decide);
      unsubscribe();
    };
  }, [roomId, tabId, claim]);

  useEffect(() => {
    if (state !== "owner" || !roomId) return;
    const beat = window.setInterval(
      () => writeClaim(roomId, tabId),
      TAB_LOCK_TIMINGS.heartbeatMs,
    );
    const release = () => {
      releaseClaim(roomId, tabId);
      postTabMessage({ type: "RELEASE", roomId, tabId });
    };
    window.addEventListener("pagehide", release);
    return () => {
      window.clearInterval(beat);
      window.removeEventListener("pagehide", release);
      release();
    };
  }, [state, roomId, tabId]);

  const requestFocusOnOwner = useCallback(() => {
    postTabMessage({ type: "FOCUS_REQUEST", roomId, tabId });
  }, [roomId, tabId]);

  return { state, requestFocusOnOwner, takeOver: claim };
}