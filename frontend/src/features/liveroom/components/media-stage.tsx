"use client";

import { useEffect, useMemo } from "react";
import { useTranslations } from "next-intl";
import { useLiveRoomMedia } from "../hooks/use-live-room-media";
import { MediaTile } from "./media-tile";
import { subscribeRoomMediaState } from "../api/ws";
import { useLiveRoomMediaStore } from "../stores/use-live-room-media-store";
import type { MediaStateChangedWsEvent } from "../types";

interface MediaStageProps {
  roomCode: string;
  localUserId: string;
  localDisplayName: string;
  enabled: boolean;
}

export function MediaStage({ roomCode, localUserId, localDisplayName, enabled }: MediaStageProps) {
  const tMedia = useTranslations("liveroom.media");
  const removeRemotePeer = useLiveRoomMediaStore((state) => state.removeRemotePeer);

  const media = useLiveRoomMedia({
    roomCode,
    localUserId,
    localDisplayName,
    enabled,
  });

  useEffect(() => {
    if (!roomCode) return undefined;
    const subscription = subscribeRoomMediaState(roomCode, (event: MediaStateChangedWsEvent) => {
      if (event.userId === localUserId) {
        const nextMic = event.micMuted;
        const nextCamera = event.cameraOff;
        const current = useLiveRoomMediaStore.getState();
        if (current.micMuted !== nextMic) {
          useLiveRoomMediaStore.getState().setMicMuted(nextMic);
        }
        if (current.cameraOff !== nextCamera) {
          useLiveRoomMediaStore.getState().setCameraOff(nextCamera);
        }
        return;
      }
      if (event.cameraOff && event.micMuted) {
        removeRemotePeer(event.userId);
      }
    });
    return () => subscription.unsubscribe();
  }, [roomCode, localUserId, removeRemotePeer]);

  const peers = media.remotePeers;

  const gridClassName = useMemo(() => {
    const totalCount = peers.length + 1;
    if (totalCount <= 1) return "grid grid-cols-1";
    if (totalCount === 2) return "grid grid-cols-1 sm:grid-cols-2";
    if (totalCount <= 4) return "grid grid-cols-2";
    if (totalCount <= 9) return "grid grid-cols-2 md:grid-cols-3";
    return "grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4";
  }, [peers.length]);

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <h2 className="text-base font-semibold text-black dark:text-white">{tMedia("title")}</h2>
      </div>
      {media.errorMessage ? (
        <div className="rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-xs text-amber-800 dark:border-amber-800 dark:bg-amber-950/30 dark:text-amber-300">
          {media.errorMessage}
        </div>
      ) : null}
      <div className={`gap-3 ${gridClassName}`}>
        <MediaTile
          stream={media.stream}
          cameraOff={media.cameraOff}
          micMuted={media.micMuted}
          displayName={localDisplayName}
          isLocal
        />
        {peers.length === 0 ? (
          <div className="flex aspect-video w-full items-center justify-center rounded-xl border border-dashed border-neutral-200 text-xs text-neutral-500 dark:border-neutral-800 dark:text-neutral-400">
            {tMedia("noParticipants")}
          </div>
        ) : null}
        {peers.map((peer) => (
          <MediaTile
            key={peer.userId}
            stream={peer.stream}
            cameraOff={false}
            micMuted={false}
            displayName={peer.displayName || peer.userId}
            isLocal={false}
          />
        ))}
      </div>
    </div>
  );
}
