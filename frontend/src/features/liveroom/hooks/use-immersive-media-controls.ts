"use client";

import { useCallback } from "react";
import {
  applyTrackMutedFlag,
  useLiveRoomMediaStore,
} from "../stores/use-live-room-media-store";
import { useUpdateMyMedia } from "../api/participants";

interface UseImmersiveMediaControlsParams {
  roomCode: string;
}

export function useImmersiveMediaControls({ roomCode }: UseImmersiveMediaControlsParams) {
  const setMicMuted = useLiveRoomMediaStore((state) => state.setMicMuted);
  const setCameraOff = useLiveRoomMediaStore((state) => state.setCameraOff);
  const updateMyMediaMutation = useUpdateMyMedia();

  const toggleMic = useCallback(() => {
    const store = useLiveRoomMediaStore.getState();
    const next = !store.micMuted;
    setMicMuted(next);
    applyTrackMutedFlag(store.localStream, "audio", next);
    if (!roomCode) return;
    updateMyMediaMutation.mutate({
      roomCode,
      body: {
        micMuted: next,
        cameraOff: store.cameraOff,
      },
    });
  }, [roomCode, setMicMuted, updateMyMediaMutation]);

  const toggleCamera = useCallback(() => {
    const store = useLiveRoomMediaStore.getState();
    const next = !store.cameraOff;
    setCameraOff(next);
    applyTrackMutedFlag(store.localStream, "video", next);
    if (!roomCode) return;
    updateMyMediaMutation.mutate({
      roomCode,
      body: {
        micMuted: store.micMuted,
        cameraOff: next,
      },
    });
  }, [roomCode, setCameraOff, updateMyMediaMutation]);

  return {
    micMuted: useLiveRoomMediaStore((state) => state.micMuted),
    cameraOff: useLiveRoomMediaStore((state) => state.cameraOff),
    toggleMic,
    toggleCamera,
  };
}