"use client";

import { useCallback } from "react";
import { useMediaSessionStore } from "../stores/use-media-session-store";
import { mediaSessionController } from "../lib/media-session";
import type { MediaDeviceInfo } from "../types";

export type { MediaPermissionError } from "../stores/use-media-session-store";
export { MediaPermissionException } from "../stores/use-media-session-store";

export interface UseMediaDevicesResult {
  stream: MediaStream | null;
  audioDevices: MediaDeviceInfo[];
  videoDevices: MediaDeviceInfo[];
  currentAudioId: string | null;
  currentVideoId: string | null;
  selectingDevice: boolean;
  permissionState: "idle" | "granted" | "denied" | "error";
  errorMessage: string | null;
  start: () => Promise<MediaStream>;
  setAudio: (deviceId: string) => Promise<MediaStream>;
  setVideo: (deviceId: string) => Promise<MediaStream>;
  enableCamera: () => Promise<MediaStreamTrack | null>;
  disableCamera: () => Promise<void>;
  stop: () => void;
  refreshDevices: () => Promise<void>;
}

export function useMediaDevices(): UseMediaDevicesResult {
  const stream = useMediaSessionStore((state) => state.stream);
  const audioDevices = useMediaSessionStore((state) => state.audioDevices);
  const videoDevices = useMediaSessionStore((state) => state.videoDevices);
  const currentAudioId = useMediaSessionStore((state) => state.currentAudioId);
  const currentVideoId = useMediaSessionStore((state) => state.currentVideoId);
  const selectingDevice = useMediaSessionStore((state) => state.selectingDevice);
  const permissionState = useMediaSessionStore((state) => state.permissionState);
  const errorMessage = useMediaSessionStore((state) => state.errorMessage);

  const start = useCallback(() => mediaSessionController.start(), []);
  const setAudio = useCallback((deviceId: string) => mediaSessionController.setAudio(deviceId), []);
  const setVideo = useCallback((deviceId: string) => mediaSessionController.setVideo(deviceId), []);
  const enableCamera = useCallback(() => mediaSessionController.enableCamera(), []);
  const disableCamera = useCallback(() => mediaSessionController.disableCamera(), []);
  const stop = useCallback(() => mediaSessionController.stop(), []);
  const refreshDevices = useCallback(() => mediaSessionController.refreshDevices(), []);

  return {
    stream,
    audioDevices,
    videoDevices,
    currentAudioId,
    currentVideoId,
    selectingDevice,
    permissionState,
    errorMessage,
    start,
    setAudio,
    setVideo,
    enableCamera,
    disableCamera,
    stop,
    refreshDevices,
  };
}
