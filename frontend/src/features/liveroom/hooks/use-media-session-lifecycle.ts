"use client";

import { useCallback, useEffect, useRef } from "react";
import { mediaSessionController } from "../lib/media-session";
import { createBeaconLeave } from "../api/leave-beacon";

interface UseMediaSessionLifecycleParams {
  roomCode: string | null;
  isHost: boolean;
  onLeaveInitiated?: () => void;
}

export function useMediaSessionLifecycle({
  roomCode,
  isHost,
  onLeaveInitiated,
}: UseMediaSessionLifecycleParams): void {
  const roomCodeRef = useRef(roomCode);
  const isHostRef = useRef(isHost);

  useEffect(() => {
    roomCodeRef.current = roomCode;
    isHostRef.current = isHost;
  }, [roomCode, isHost]);

  useEffect(() => {
    const releaseSessionAndReport = (): void => {
      const currentRoomCode = roomCodeRef.current;

      if (currentRoomCode && !isHostRef.current) {
        createBeaconLeave(currentRoomCode);
      }

      mediaSessionController.releaseForUnload();
      onLeaveInitiated?.();
    };

    const onPageHide = () => {
      releaseSessionAndReport();
    };

    const onBeforeUnload = () => {
      releaseSessionAndReport();
    };

    window.addEventListener("pagehide", onPageHide);
    window.addEventListener("beforeunload", onBeforeUnload);

    return () => {
      window.removeEventListener("pagehide", onPageHide);
      window.removeEventListener("beforeunload", onBeforeUnload);
    };
  }, [onLeaveInitiated]);
}

export function leaveMediaSession(roomCode: string | null, isHost: boolean): void {
  if (roomCode && !isHost) {
    createBeaconLeave(roomCode);
  }
  mediaSessionController.releaseForUnload();
}
