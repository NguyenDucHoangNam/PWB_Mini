"use client";

import { useCallback } from "react";
import { useLeaveRoom } from "./participants";

interface BeaconLeaveOptions {
  roomCode: string;
  isHost: boolean;
}

export function useBeaconLeave() {
  const { mutate: leaveMutate } = useLeaveRoom({});

  const beaconLeave = useCallback((options: BeaconLeaveOptions) => {
    const { roomCode, isHost } = options;

    if (isHost) {
      return;
    }

    const endpoint = `/api/v1/live-rooms/${roomCode}/leave`;

    if (navigator.sendBeacon) {
      const data = JSON.stringify({ roomCode });
      const blob = new Blob([data], { type: "application/json" });
      navigator.sendBeacon(endpoint, blob);
    } else {
      fetch(endpoint, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ roomCode }),
        keepalive: true,
      }).catch(() => {});
    }

    leaveMutate({ roomCode });
  }, [leaveMutate]);

  return { beaconLeave };
}

export function createBeaconLeave(roomCode: string): void {
  const endpoint = `/api/v1/live-rooms/${roomCode}/leave`;

  if (navigator.sendBeacon) {
    const blob = new Blob([], { type: "application/json" });
    navigator.sendBeacon(endpoint, blob);
  } else {
    fetch(endpoint, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      keepalive: true,
    }).catch(() => {});
  }
}
