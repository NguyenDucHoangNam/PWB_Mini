"use client";

import { useEffect, useMemo, useRef } from "react";
import { useLiveRoomMedia } from "../hooks/use-live-room-media";
import { useActiveSpeaker } from "../hooks/use-active-speaker";
import { MediaTile } from "./media-tile";
import { MediaControls } from "./media-controls";
import { useParticipants } from "../api/participants";
import { useLiveRoomMediaStore } from "../stores/use-live-room-media-store";

export interface MediaStageControls {
  toggleMic: () => void;
  toggleCamera: () => void;
  micMuted: boolean;
  cameraOff: boolean;
}

interface MediaStageProps {
  roomCode: string;
  localUserId: string;
  localDisplayName: string;
  enabled: boolean;
  onLeave?: () => void;
  showControls?: boolean;
  onMediaReady?: (controls: MediaStageControls) => void;
}

export function MediaStage({
  roomCode,
  localUserId,
  localDisplayName,
  enabled,
  onLeave,
  showControls = true,
  onMediaReady,
}: MediaStageProps) {
  const media = useLiveRoomMedia({
    roomCode,
    localUserId,
    localDisplayName,
    enabled,
  });

  useActiveSpeaker(localUserId);
  const speakingUsers = useLiveRoomMediaStore((state) => state.speakingUsers);
  const remoteMediaStates = useLiveRoomMediaStore((state) => state.remoteMediaStates);

  const { data: participantsRes } = useParticipants({ roomCode });
  const participantMap = useMemo(() => {
    const map = new Map<string, string>();
    for (const p of participantsRes?.data ?? []) {
      map.set(p.userId, p.displayName || p.email || p.userId);
    }
    return map;
  }, [participantsRes]);

  useEffect(() => {
    const participants = participantsRes?.data;
    if (!participants) return;
    const toSeed: Record<string, { micMuted: boolean; cameraOff: boolean }> = {};
    const current = useLiveRoomMediaStore.getState().remoteMediaStates;
    for (const p of participants) {
      if (p.userId === localUserId) continue;
      if (current[p.userId] !== undefined) continue;
      toSeed[p.userId] = { micMuted: p.micMuted, cameraOff: p.cameraOff };
    }
    if (Object.keys(toSeed).length > 0) {
      useLiveRoomMediaStore.getState().bulkSetRemoteMediaStates(toSeed);
    }
  }, [participantsRes, localUserId]);

  const onMediaReadyRef = useRef(onMediaReady);
  useEffect(() => {
    onMediaReadyRef.current = onMediaReady;
  }, [onMediaReady]);

  useEffect(() => {
    onMediaReadyRef.current?.({
      toggleMic: media.toggleMic,
      toggleCamera: media.toggleCamera,
      micMuted: media.micMuted,
      cameraOff: media.cameraOff,
    });
  }, [media.toggleMic, media.toggleCamera, media.micMuted, media.cameraOff]);

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
          const remoteState = remoteMediaStates[peer.userId];
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
