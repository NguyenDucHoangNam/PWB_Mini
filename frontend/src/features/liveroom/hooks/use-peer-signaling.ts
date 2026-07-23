"use client";

import { useEffect, useRef } from "react";
import { requestRoomState, subscribeRoomPeerEvents, subscribeRoomState } from "../api/ws";
import type { PeerJoinedWsEvent, PeerLeftWsEvent, RoomStateWsEvent } from "../types";

interface UsePeerSignalingParams {
  roomCode: string;
  managerRef: React.MutableRefObject<{
    addPeer: (remoteUserId: string, displayName?: string) => Promise<void>;
    queuePeerIfNeeded: (remoteUserId: string, displayName?: string) => Promise<void>;
    removePeer: (remoteUserId: string) => void;
  } | null>;
  enabled: boolean;
  onRemoteUserJoined?: (event: PeerJoinedWsEvent) => void;
  onRemoteUserLeft?: (event: PeerLeftWsEvent) => void;
  onRoomStateReceived?: (event: RoomStateWsEvent) => void;
}

export function usePeerSignaling({
  roomCode,
  managerRef,
  enabled,
  onRemoteUserJoined,
  onRemoteUserLeft,
  onRoomStateReceived,
}: UsePeerSignalingParams): void {
  const joinedHandlerRef = useRef<typeof onRemoteUserJoined>(undefined);
  const leftHandlerRef = useRef<typeof onRemoteUserLeft>(undefined);
  const roomStateHandlerRef = useRef<typeof onRoomStateReceived>(undefined);

  useEffect(() => {
    joinedHandlerRef.current = onRemoteUserJoined;
    leftHandlerRef.current = onRemoteUserLeft;
    roomStateHandlerRef.current = onRoomStateReceived;
  }, [onRemoteUserJoined, onRemoteUserLeft, onRoomStateReceived]);

  useEffect(() => {
    if (!roomCode || !enabled) return undefined;

    const peerSubscription = subscribeRoomPeerEvents(roomCode, (event) => {
      if (event.type === "PEER_JOINED") {
        joinedHandlerRef.current?.(event);
        void managerRef.current?.queuePeerIfNeeded(event.userId, event.displayName);
        return;
      }
      if (event.type === "PEER_LEFT") {
        managerRef.current?.removePeer(event.userId);
        leftHandlerRef.current?.(event);
      }
    });

    const stateSubscription = subscribeRoomState(roomCode, (event) => {
      roomStateHandlerRef.current?.(event);
      for (const peer of event.participants) {
        void managerRef.current?.queuePeerIfNeeded(peer.userId, peer.displayName);
      }
    });

    const requestTimer = setTimeout(() => {
      requestRoomState(roomCode);
    }, 250);

    return () => {
      clearTimeout(requestTimer);
      peerSubscription.unsubscribe();
      stateSubscription.unsubscribe();
    };
  }, [roomCode, enabled, managerRef]);
}
