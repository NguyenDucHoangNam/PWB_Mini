"use client";

import { useEffect, useRef } from "react";
import { subscribeRoomPeerEvents } from "../api/ws";
import type { PeerJoinedWsEvent, PeerLeftWsEvent } from "../types";

interface UsePeerSignalingParams {
  roomCode: string;
  managerRef: React.MutableRefObject<{
    addPeer: (remoteUserId: string) => Promise<void>;
    removePeer: (remoteUserId: string) => void;
  } | null>;
  localStream: MediaStream | null;
  enabled: boolean;
  onRemoteUserJoined?: (event: PeerJoinedWsEvent) => void;
  onRemoteUserLeft?: (event: PeerLeftWsEvent) => void;
}

export function usePeerSignaling({
  roomCode,
  managerRef,
  localStream,
  enabled,
  onRemoteUserJoined,
  onRemoteUserLeft,
}: UsePeerSignalingParams): void {
  const joinedHandlerRef = useRef<typeof onRemoteUserJoined>(undefined);
  const leftHandlerRef = useRef<typeof onRemoteUserLeft>(undefined);

  useEffect(() => {
    joinedHandlerRef.current = onRemoteUserJoined;
    leftHandlerRef.current = onRemoteUserLeft;
  }, [onRemoteUserJoined, onRemoteUserLeft]);

  useEffect(() => {
    if (!roomCode || !enabled || !localStream) return undefined;

    const subscription = subscribeRoomPeerEvents(roomCode, (event) => {
      if (event.type === "PEER_JOINED") {
        joinedHandlerRef.current?.(event);
        void managerRef.current?.addPeer(event.userId);
        return;
      }
      if (event.type === "PEER_LEFT") {
        managerRef.current?.removePeer(event.userId);
        leftHandlerRef.current?.(event);
      }
    });

    return () => subscription.unsubscribe();
  }, [roomCode, enabled, localStream, managerRef]);
}
