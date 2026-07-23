"use client";

import { create } from "zustand";
import type { MediaDeviceInfo } from "../types";

export type MediaPermissionError =
  | "PERMISSION_DENIED"
  | "POLICY_BLOCKED"
  | "DEVICE_NOT_FOUND"
  | "UNKNOWN";

export class MediaPermissionException extends Error {
  readonly kind: MediaPermissionError;

  constructor(kind: MediaPermissionError, message: string) {
    super(message);
    this.kind = kind;
    this.name = "MediaPermissionException";
  }
}

type PermissionState = "idle" | "granted" | "denied" | "error";

export interface MediaSessionState {
  stream: MediaStream | null;
  streamRevision: number;
  audioDevices: MediaDeviceInfo[];
  videoDevices: MediaDeviceInfo[];
  currentAudioId: string | null;
  currentVideoId: string | null;
  selectingDevice: boolean;
  permissionState: PermissionState;
  errorMessage: string | null;
  cameraReleasing: boolean;
  cameraReacquiring: boolean;
}

interface MediaSessionActions {
  setStream: (stream: MediaStream | null) => void;
  bumpStreamRevision: () => void;
  setDevices: (audios: MediaDeviceInfo[], videos: MediaDeviceInfo[]) => void;
  setCurrentAudioId: (id: string | null) => void;
  setCurrentVideoId: (id: string | null) => void;
  setSelectingDevice: (busy: boolean) => void;
  setPermissionState: (state: PermissionState) => void;
  setErrorMessage: (msg: string | null) => void;
  setCameraBusy: (releasing: boolean, reacquiring: boolean) => void;
  reset: () => void;
}

export type MediaSessionStore = MediaSessionState & MediaSessionActions;

const initialState: MediaSessionState = {
  stream: null,
  streamRevision: 0,
  audioDevices: [],
  videoDevices: [],
  currentAudioId: null,
  currentVideoId: null,
  selectingDevice: false,
  permissionState: "idle",
  errorMessage: null,
  cameraReleasing: false,
  cameraReacquiring: false,
};

export const useMediaSessionStore = create<MediaSessionStore>((set) => ({
  ...initialState,
  setStream: (stream) => set({ stream }),
  bumpStreamRevision: () => set((s) => ({ streamRevision: s.streamRevision + 1 })),
  setDevices: (audioDevices, videoDevices) => set({ audioDevices, videoDevices }),
  setCurrentAudioId: (currentAudioId) => set({ currentAudioId }),
  setCurrentVideoId: (currentVideoId) => set({ currentVideoId }),
  setSelectingDevice: (selectingDevice) => set({ selectingDevice }),
  setPermissionState: (permissionState) => set({ permissionState }),
  setErrorMessage: (errorMessage) => set({ errorMessage }),
  setCameraBusy: (cameraReleasing, cameraReacquiring) =>
    set({ cameraReleasing, cameraReacquiring }),
  reset: () => set({ ...initialState }),
}));
