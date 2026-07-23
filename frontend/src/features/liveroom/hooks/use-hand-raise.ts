"use client";

import { useCallback, useEffect } from "react";
import { useHandRaiseStore } from "../stores/hand-raise-store";
import { subscribeRoomParticipants } from "../api/ws";
import type { ParticipantWsEvent } from "../types";

export function useHandRaise({
  roomCode,
  localUserId,
  enabled,
  onBroadcast,
}: {
  roomCode: string;
  localUserId: string;
  enabled: boolean;
  onBroadcast?: (action: "RAISE" | "LOWER") => void;
}) {
  const raise = useHandRaiseStore((state) => state.raise);
  const lower = useHandRaiseStore((state) => state.lower);

  useEffect(() => {
    if (!enabled || !roomCode) return undefined;
    const subscription = subscribeRoomParticipants(roomCode, (event: ParticipantWsEvent) => {
      if (!event.userId || event.userId === localUserId) return;
      if (event.type === "HAND_RAISED") {
        raise(event.userId);
      } else if (event.type === "HAND_LOWERED") {
        lower(event.userId);
      }
    });
    return () => subscription.unsubscribe();
  }, [enabled, roomCode, localUserId, raise, lower]);

  const toggle = useCallback(
    (isCurrentlyRaised: boolean) => {
      const action = isCurrentlyRaised ? "LOWER" : "RAISE";
      if (action === "RAISE") {
        raise(localUserId);
      } else {
        lower(localUserId);
      }
      onBroadcast?.(action);
    },
    [localUserId, raise, lower, onBroadcast],
  );

  return { toggle };
}
