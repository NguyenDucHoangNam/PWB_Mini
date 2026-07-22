"use client";

import { useEffect, useMemo } from "react";
import { useTranslations } from "next-intl";
import { useLiveRoomMedia } from "../hooks/use-live-room-media";
import { MediaTile } from "./media-tile";
import { MediaControls } from "./media-controls";
import { subscribeRoomMediaState } from "../api/ws";
import { useLiveRoomMediaStore } from "../stores/use-live-room-media-store";
import type { MediaStateChangedWsEvent } from "../types";

interface MediaStageProps {
  roomCode: string;
  localUserId: string;
  localDisplayName: string;
  enabled: boolean;
  onLeave?: () => void;
}

export function MediaStage({ roomCode, localUserId, localDisplayName, enabled, onLeave }: MediaStageProps) {
  const tMedia = useTranslations("liveroom.media");

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
      }
    });
    return () => subscription.unsubscribe();
  }, [roomCode, localUserId]);

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
    <div className="flex flex-1 flex-col p-3">
      {media.errorMessage ? (
        <div className="mb-2 rounded-lg border border-amber-600 bg-amber-950/50 px-3 py-2 text-xs text-amber-300">
          {media.errorMessage}
        </div>
      ) : null}
      <div className={`grid flex-1 auto-rows-fr gap-3 ${gridClassName}`}>
        <MediaTile
          stream={media.stream}
          cameraOff={media.cameraOff}
          micMuted={media.micMuted}
          displayName={localDisplayName}
          isLocal
        />
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
      <div className="absolute inset-x-0 bottom-0 z-10">
        <div className="flex items-center justify-center px-4 pb-6">
          <MediaControls
            micMuted={media.micMuted}
            cameraOff={media.cameraOff}
            selectingDevice={media.selectingDevice}
            audioDevices={media.audioDevices}
            videoDevices={media.videoDevices}
            currentAudioId={media.currentAudioId}
            currentVideoId={media.currentVideoId}
            onToggleMic={media.toggleMic}
            onToggleCamera={media.toggleCamera}
            onSelectAudio={media.setAudioDevice}
            onSelectVideo={media.setVideoDevice}
            onLeave={onLeave ?? media.stop}
          />
        </div>
      </div>
    </div>
  );
}
