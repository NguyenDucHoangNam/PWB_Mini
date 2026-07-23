"use client";

import { useEffect, useMemo, useState } from "react";
import { useLiveRoomMedia } from "../hooks/use-live-room-media";
import { useActiveSpeaker } from "../hooks/use-active-speaker";
import { MediaTile } from "./media-tile";
import { MediaControls } from "./media-controls";
import { subscribeRoomMediaState } from "../api/ws";
import { useParticipants } from "../api/participants";
import { useLiveRoomMediaStore } from "../stores/use-live-room-media-store";
import type { MediaStateChangedWsEvent } from "../types";

interface MediaStageProps {
  roomCode: string;
  localUserId: string;
  localDisplayName: string;
  enabled: boolean;
  onLeave?: () => void;
  showControls?: boolean;
}

export function MediaStage({
  roomCode,
  localUserId,
  localDisplayName,
  enabled,
  onLeave,
  showControls = true,
}: MediaStageProps) {
  const media = useLiveRoomMedia({
    roomCode,
    localUserId,
    localDisplayName,
    enabled,
  });

  useActiveSpeaker(localUserId);
  const speakingUsers = useLiveRoomMediaStore((state) => state.speakingUsers);

  const { data: participantsRes } = useParticipants({ roomCode });
  const participantMap = useMemo(() => {
    const map = new Map<string, string>();
    for (const p of participantsRes?.data ?? []) {
      map.set(p.userId, p.displayName || p.email || p.userId);
    }
    return map;
  }, [participantsRes]);

  const [remoteMediaState, setRemoteMediaState] = useState<Record<string, { micMuted: boolean; cameraOff: boolean }>>({});

  useEffect(() => {
    if (!roomCode) return undefined;
    const subscription = subscribeRoomMediaState(roomCode, (event: MediaStateChangedWsEvent) => {
      if (event.userId === localUserId) {
        media.syncFromServer({ micMuted: event.micMuted, cameraOff: event.cameraOff });
        return;
      }
      setRemoteMediaState((prev) => ({
        ...prev,
        [event.userId]: { micMuted: event.micMuted, cameraOff: event.cameraOff },
      }));
    });
    return () => subscription.unsubscribe();
  }, [roomCode, localUserId, media]);

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
          isSpeaking={speakingUsers.has(localUserId)}
        />
        {peers.map((peer) => {
          const remoteState = remoteMediaState[peer.userId];
          const name =
            participantMap.get(peer.userId) ||
            (peer.displayName && peer.displayName !== peer.userId ? peer.displayName : peer.userId);
          return (
            <MediaTile
              key={peer.userId}
              stream={peer.stream}
              cameraOff={remoteState?.cameraOff ?? false}
              micMuted={remoteState?.micMuted ?? false}
              displayName={name}
              isLocal={false}
              isSpeaking={speakingUsers.has(peer.userId)}
            />
          );
        })}
      </div>
      {showControls ? (
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
      ) : null}
    </div>
  );
}
