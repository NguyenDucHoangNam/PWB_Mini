"use client";

import { useEffect, useRef } from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { appDestinations } from "../lib/liveroom-destinations";
import { liveroomSocket } from "../lib/liveroom-socket";
import { PeerConnectionManager } from "../lib/peer-connection-manager";
import { useLiveroomStore } from "../stores/use-liveroom-store";
import type { LocalMediaState } from "./use-local-media";

interface UsePeerMeshOptions {
  roomId: string;
  enabled: boolean;
  media: LocalMediaState;
}

export function usePeerMesh({ roomId, enabled, media }: UsePeerMeshOptions): void {
  const tErrors = useTranslations("liveroom.errors");
  const managerRef = useRef<PeerConnectionManager | null>(null);
  const config = useLiveroomStore((state) => state.rtc.config);

  useEffect(() => {
    if (!enabled || !config || !roomId) return;

    const manager = new PeerConnectionManager({
      config,
      publishOffer: (targetUserId, sdp) =>
        liveroomSocket.publish(appDestinations.rtcOffer(roomId), { targetUserId, sdp }),
      publishAnswer: (targetUserId, sdp) =>
        liveroomSocket.publish(appDestinations.rtcAnswer(roomId), { targetUserId, sdp }),
      publishIce: (targetUserId, candidate) =>
        liveroomSocket.publish(appDestinations.rtcIce(roomId), {
          targetUserId,
          candidate: candidate.candidate,
          sdpMid: candidate.sdpMid,
          sdpMLineIndex: candidate.sdpMLineIndex,
          usernameFragment: candidate.usernameFragment,
        }),
      onRemoteStream: (userId, stream) =>
        useLiveroomStore.getState().setPeerStream(userId, stream),
      onPeerState: (userId, state) => useLiveroomStore.getState().setPeerState(userId, state),
      onPayloadTooLarge: () => toast.error(tErrors("rtcPayloadTooLarge")),
    });
    managerRef.current = manager;

    const offEvent = liveroomSocket.onEvent((event) => {
      const store = useLiveroomStore.getState();
      const myUserId = store.myUserId;

      if (event.type === "RTC_OFFER") {
        if (event.data.fromUserId === myUserId) return;
        void manager.handleOffer(event.data.fromUserId, event.data.sdp);
        return;
      }
      if (event.type === "RTC_ANSWER") {
        if (event.data.fromUserId === myUserId) return;
        void manager.handleAnswer(event.data.fromUserId, event.data.sdp);
        return;
      }
      if (event.type === "RTC_ICE_CANDIDATE") {
        if (event.data.fromUserId === myUserId) return;
        void manager.handleIce(event.data.fromUserId, {
          candidate: event.data.candidate,
          sdpMid: event.data.sdpMid ?? undefined,
          sdpMLineIndex: event.data.sdpMLineIndex ?? undefined,
          usernameFragment: event.data.usernameFragment ?? undefined,
        });
        return;
      }
      if (event.type === "PARTICIPANT_JOINED") {
        if (event.data.userId === myUserId) return;
        void manager.callNewcomer(event.data.userId);
        return;
      }
      if (event.type === "PARTICIPANT_LEFT" || event.type === "PARTICIPANT_KICKED") {
        manager.removePeer(event.data.userId);
        useLiveroomStore.getState().removePeer(event.data.userId);
      }
    });

    return () => {
      offEvent();
      manager.destroy();
      managerRef.current = null;
    };
  }, [enabled, config, roomId, tErrors]);

  useEffect(() => {
    managerRef.current?.setAudioTrack(media.micOn ? media.audioTrack : null);
  }, [media.audioTrack, media.micOn]);

  useEffect(() => {
    managerRef.current?.setVideoTrack(media.videoTrack);
  }, [media.videoTrack]);

  const participants = useLiveroomStore((state) => state.participants);


  useEffect(() => {
    const manager = managerRef.current;
    if (!manager || !enabled) return;

    const store = useLiveroomStore.getState();
    const myUserId = store.myUserId;
    if (!myUserId) return;

    const me = participants[myUserId];
    if (!me) return;
    const myJoinedAt = Date.parse(me.joinedAt);

    manager.peerIds.forEach((userId) => {
      if (!participants[userId]) {
        manager.removePeer(userId);
        useLiveroomStore.getState().removePeer(userId);
      }
    });

    Object.values(participants).forEach((participant) => {
      if (participant.userId === myUserId) return;
      if (manager.peerIds.includes(participant.userId)) return;
      const theirJoinedAt = Date.parse(participant.joinedAt);
      if (Number.isNaN(theirJoinedAt) || theirJoinedAt > myJoinedAt) {
        void manager.callNewcomer(participant.userId);
      }
    });
  }, [participants, enabled]);
}