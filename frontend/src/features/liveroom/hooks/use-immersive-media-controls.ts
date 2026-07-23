"use client";

import { useCallback } from "react";
import { useLiveRoomMediaStore } from "../stores/use-live-room-media-store";
import { useMediaDevices } from "./use-media-devices";
import { useUpdateMyMedia } from "../api/participants";

interface UseImmersiveMediaControlsParams {
  roomCode: string;
}

export function useImmersiveMediaControls({ roomCode }: UseImmersiveMediaControlsParams) {
  const setMicMuted = useLiveRoomMediaStore((state) => state.setMicMuted);
  const setCameraOff = useLiveRoomMediaStore((state) => state.setCameraOff);
  const devices = useMediaDevices();
  const updateMyMediaMutation = useUpdateMyMedia();

  const toggleMic = useCallback(() => {
    const store = useLiveRoomMediaStore.getState();
    const next = !store.micMuted;
    setMicMuted(next);
    const stream = devices.stream;
    if (stream) {
      for (const track of stream.getAudioTracks()) {
        track.enabled = !next;
      }
    }
    if (!roomCode) return;
    updateMyMediaMutation.mutate({
      roomCode,
      body: {
        micMuted: next,
        cameraOff: store.cameraOff,
      },
    });
  }, [devices.stream, roomCode, setMicMuted, updateMyMediaMutation]);

  const toggleCamera = useCallback(async () => {
    const store = useLiveRoomMediaStore.getState();
    const next = !store.cameraOff;
    setCameraOff(next);
    try {
      if (next) {
        await devices.disableCamera();
      } else {
        await devices.enableCamera();
      }
    } catch {
      setCameraOff(!next);
      return;
    }
    if (!roomCode) return;
    updateMyMediaMutation.mutate({
      roomCode,
      body: {
        micMuted: store.micMuted,
        cameraOff: next,
      },
    });
  }, [devices, roomCode, setCameraOff, updateMyMediaMutation]);

  return {
    micMuted: useLiveRoomMediaStore((state) => state.micMuted),
    cameraOff: useLiveRoomMediaStore((state) => state.cameraOff),
    toggleMic,
    toggleCamera,
  };
}
