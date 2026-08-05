"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { getChatHistory } from "../api/chat";
import { joinRoom, listParticipants } from "../api/participants";
import { listPendingJoinRequests } from "../api/join-requests";
import { getRoom } from "../api/rooms";
import { getRtcConfig } from "../api/rtc-config";
import { appDestinations } from "../lib/liveroom-destinations";
import { liveroomSocket } from "../lib/liveroom-socket";
import { ALREADY_SEATED_CODES, LiveroomErrorCode } from "../lib/liveroom-error-codes";
import { liveroomErrorCodeOf } from "../lib/resolve-liveroom-error-message";
import { useLiveroomStore } from "../stores/use-liveroom-store";

export type RoomSessionPhase =
  | "connecting"
  | "joining"
  | "loading"
  | "ready"
  | "denied"
  | "kicked"
  | "ended"
  | "error";

interface RoomSessionResult {
  phase: RoomSessionPhase;
  errorCode: string | null;
  retry: () => void;
}

export function useRoomSession(roomId: string, myUserId: string | null): RoomSessionResult {
  const [phase, setPhase] = useState<RoomSessionPhase>("connecting");
  const [errorCode, setErrorCode] = useState<string | null>(null);
  const [nonce, setNonce] = useState(0);
  const seatedRef = useRef(false);
  const connectionStatus = useLiveroomStore((state) => state.connection.status);

  const retry = useCallback(() => {
    seatedRef.current = false;
    liveroomSocket.retry();
    setNonce((value) => value + 1);
  }, []);

  useEffect(() => {
    if (!roomId || !myUserId) return;
    const store = useLiveroomStore.getState();
    store.reset(roomId, myUserId, false);
    seatedRef.current = false;
    liveroomSocket.connect();
  }, [roomId, myUserId, nonce]);

  const loadSnapshot = useCallback(async () => {
    const store = useLiveroomStore.getState();
    store.beginBuffering();
    try {
      const [roomRes, participantsRes] = await Promise.all([
        getRoom({ roomId }),
        listParticipants({ roomId }),
      ]);
      if (roomRes.data) store.applyRoom(roomRes.data);
      if (participantsRes.data) store.applyParticipants(participantsRes.data);

      const isOwner = useLiveroomStore.getState().isOwner;
      const [chatRes, rtcRes, requestsRes] = await Promise.all([
        getChatHistory({ roomId }).catch(() => null),
        getRtcConfig({ roomId }).catch(() => null),
        isOwner ? listPendingJoinRequests({ roomId }).catch(() => null) : Promise.resolve(null),
      ]);
      if (chatRes?.data) {
        store.applyChatHistory(
          [...chatRes.data.messages].reverse(),
          chatRes.data.hasMore,
          chatRes.data.nextCursor,
        );
      }
      if (rtcRes?.data) store.applyRtcConfig(rtcRes.data);
      if (requestsRes?.data) store.applyJoinRequests(requestsRes.data);
    } finally {
      store.drainBuffer();
    }


    liveroomSocket.publish(appDestinations.musicGetState(roomId));
  }, [roomId]);

  const seatAndSubscribe = useCallback(async () => {
    setPhase("joining");
    try {
      await joinRoom({ roomId });
    } catch (error) {
      const code = liveroomErrorCodeOf(error);
      if (!code || !ALREADY_SEATED_CODES.has(code)) {
        setErrorCode(code);
        if (code === LiveroomErrorCode.APPROVAL_REQUIRED) setPhase("denied");
        else if (code === LiveroomErrorCode.KICKED_COOLDOWN) setPhase("kicked");
        else if (code === LiveroomErrorCode.ROOM_ENDED) setPhase("ended");
        else if (code === LiveroomErrorCode.ROOM_NOT_FOUND) setPhase("denied");
        else setPhase("error");
        return;
      }
    }

    seatedRef.current = true;
    liveroomSocket.ensureRoomSubscriptions(roomId, true);
    setPhase("loading");
    try {
      await loadSnapshot();
      setPhase("ready");
    } catch (error) {
      setErrorCode(liveroomErrorCodeOf(error));
      setPhase("error");
    }
  }, [roomId, loadSnapshot]);

  useEffect(() => {
    if (connectionStatus !== "connected" || !roomId || !myUserId) return;
    if (seatedRef.current) {
      liveroomSocket.ensureRoomSubscriptions(roomId, true);
      void loadSnapshot();
      return;
    }
    void seatAndSubscribe();
  }, [connectionStatus, roomId, myUserId, seatAndSubscribe, loadSnapshot]);

  const unauthorized = connectionStatus === "unauthorized";

  return {
    phase: unauthorized ? "error" : phase,
    errorCode: unauthorized ? LiveroomErrorCode.WS_UNAUTHORIZED : errorCode,
    retry,
  };
}