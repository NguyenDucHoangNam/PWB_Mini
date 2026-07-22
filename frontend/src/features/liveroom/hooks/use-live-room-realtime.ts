"use client";

import { useEffect } from "react";
import {
  subscribeRoomJoinRequests,
  subscribeRoomParticipants,
  subscribeUserJoinRequestDecisions,
  type Subscription,
} from "../api/ws";
import type {
  JoinRequestCreatedWsEvent,
  JoinRequestDecidedWsEvent,
  ParticipantWsEvent,
} from "../types";

interface UseLiveRoomRealtimeParams {
  roomCode: string;
  isHost: boolean;
  onJoinRequestCreated?: (event: JoinRequestCreatedWsEvent) => void;
  onJoinRequestDecided?: (event: JoinRequestDecidedWsEvent) => void;
  onParticipantChanged?: (event: ParticipantWsEvent) => void;
}

export function useLiveRoomRealtime({
  roomCode,
  isHost,
  onJoinRequestCreated,
  onJoinRequestDecided,
  onParticipantChanged,
}: UseLiveRoomRealtimeParams): void {
  useEffect(() => {
    if (!roomCode) return;
    const subs: Subscription[] = [];
    if (isHost && onJoinRequestCreated) {
      subs.push(subscribeRoomJoinRequests(roomCode, onJoinRequestCreated));
    }
    if (!isHost && onJoinRequestDecided) {
      subs.push(subscribeUserJoinRequestDecisions(onJoinRequestDecided));
    }
    if (onParticipantChanged) {
      subs.push(subscribeRoomParticipants(roomCode, onParticipantChanged));
    }
    return () => {
      subs.forEach((s) => s.unsubscribe());
    };
  }, [
    roomCode,
    isHost,
    onJoinRequestCreated,
    onJoinRequestDecided,
    onParticipantChanged,
  ]);
}
