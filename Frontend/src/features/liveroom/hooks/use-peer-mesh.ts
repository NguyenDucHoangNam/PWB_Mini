"use client";

import { useCallback, useEffect, useRef } from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { appDestinations } from "../lib/liveroom-destinations";
import { liveroomSocket } from "../lib/liveroom-socket";
import { PeerConnectionManager } from "../lib/peer-connection-manager";
import { useLiveroomStore } from "../stores/use-liveroom-store";
import type { Participant } from "../types";
import type { LocalMediaState } from "./use-local-media";

interface UsePeerMeshOptions {
  roomId: string;
  enabled: boolean;
  media: LocalMediaState;
}

const HEAL_INTERVAL_MS = 5000;
const STALL_THRESHOLD_MS = 8000;

function joinedRank(participant: Participant): number {
  const parsed = Date.parse(participant.joinedAt);
  return Number.isNaN(parsed) ? 0 : parsed;
}

function arrivesAfter(me: Participant, other: Participant): boolean {
  const mine = joinedRank(me);
  const theirs = joinedRank(other);
  if (mine !== theirs) return mine > theirs;
  return me.userId > other.userId;
}

export function usePeerMesh({ roomId, enabled, media }: UsePeerMeshOptions): void {
  const tErrors = useTranslations("liveroom.errors");
  const managerRef = useRef<PeerConnectionManager | null>(null);
  const mediaRef = useRef(media);
  const tErrorsRef = useRef(tErrors);

  const config = useLiveroomStore((state) => state.rtc.config);

  useEffect(() => {
    mediaRef.current = media;
    tErrorsRef.current = tErrors;
  });

  const warnPayloadTooLarge = useCallback(() => {
    toast.error(tErrorsRef.current("rtcPayloadTooLarge"));
  }, []);

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
      onRemoteVideoActive: (userId, active) =>
        useLiveroomStore.getState().setPeerVideoActive(userId, active),
      onPeerState: (userId, state) => useLiveroomStore.getState().setPeerState(userId, state),
      onPayloadTooLarge: warnPayloadTooLarge,
    });

    const current = mediaRef.current;
    manager.setAudioTrack(current.micOn ? current.audioTrack : null);
    manager.setVideoTrack(current.videoTrack);
    managerRef.current = manager;

    const syncPeers = (healStalled = false) => {
      const store = useLiveroomStore.getState();
      const myUserId = store.myUserId;
      if (!myUserId) return;

      const participants = store.participants;
      const me = participants[myUserId];
      if (!me) return;

      manager.peerIds.forEach((userId) => {
        if (participants[userId]) return;
        manager.removePeer(userId);
        useLiveroomStore.getState().removePeer(userId);
      });

      Object.values(participants).forEach((participant) => {
        if (participant.userId === myUserId) return;
        if (!arrivesAfter(me, participant)) return;
        if (!manager.hasPeer(participant.userId)) {
          void manager.callPeer(participant.userId);
          return;
        }
        if (healStalled && manager.isStalled(participant.userId, STALL_THRESHOLD_MS)) {
          void manager.recall(participant.userId);
        }
      });
    };

    const offEvent = liveroomSocket.onEvent((event) => {
      const myUserId = useLiveroomStore.getState().myUserId;

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
      if (event.type === "PARTICIPANT_LEFT" || event.type === "PARTICIPANT_KICKED") {
        if (event.data.userId === myUserId) return;
        manager.removePeer(event.data.userId);
        useLiveroomStore.getState().removePeer(event.data.userId);
      }
    });

    const offParticipants = useLiveroomStore.subscribe((state, previous) => {
      if (state.participants !== previous.participants) syncPeers();
    });

    const healTimer = window.setInterval(() => syncPeers(true), HEAL_INTERVAL_MS);

    syncPeers();

    return () => {
      offEvent();
      offParticipants();
      window.clearInterval(healTimer);
      manager.destroy();
      if (managerRef.current === manager) managerRef.current = null;
    };
  }, [enabled, config, roomId, warnPayloadTooLarge]);

  useEffect(() => {
    managerRef.current?.setAudioTrack(media.micOn ? media.audioTrack : null);
  }, [media.audioTrack, media.micOn]);

  useEffect(() => {
    managerRef.current?.setVideoTrack(media.videoTrack);
  }, [media.videoTrack]);
}