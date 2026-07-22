"use client";

import { useCallback, useEffect, useRef, useState } from "react";
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

interface UseMediaDevicesResult {
  stream: MediaStream | null;
  audioDevices: MediaDeviceInfo[];
  videoDevices: MediaDeviceInfo[];
  currentAudioId: string | null;
  currentVideoId: string | null;
  selectingDevice: boolean;
  permissionState: "idle" | "granted" | "denied" | "error";
  errorMessage: string | null;
  start: () => Promise<void>;
  setAudio: (deviceId: string) => Promise<void>;
  setVideo: (deviceId: string) => Promise<void>;
  stop: () => void;
}

const DEFAULT_MEDIA_CONSTRAINTS: MediaStreamConstraints = {
  audio: true,
  video: true,
};

function readErrorMessage(err: unknown): string {
  if (err instanceof Error) return err.message;
  return "Failed to access media devices.";
}

function readPermissionDeniedMessage(err: unknown): string {
  const msg = readErrorMessage(err).toLowerCase();
  if (msg.includes("permissions policy") || msg.includes("not allowed in this document")) {
    return "PERMISSION_POLICY_BLOCKED";
  }
  return "PERMISSION_DENIED";
}

export function useMediaDevices(): UseMediaDevicesResult {
  const [stream, setStream] = useState<MediaStream | null>(null);
  const [audioDevices, setAudioDevices] = useState<MediaDeviceInfo[]>([]);
  const [videoDevices, setVideoDevices] = useState<MediaDeviceInfo[]>([]);
  const [currentAudioId, setCurrentAudioId] = useState<string | null>(null);
  const [currentVideoId, setCurrentVideoId] = useState<string | null>(null);
  const [selectingDevice, setSelectingDevice] = useState(false);
  const [permissionState, setPermissionState] = useState<
    "idle" | "granted" | "denied" | "error"
  >("idle");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const currentAudioIdRef = useRef<string | null>(null);
  const currentVideoIdRef = useRef<string | null>(null);

  const stopStream = useCallback(() => {
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((track) => track.stop());
      streamRef.current = null;
      setStream(null);
    }
  }, []);

  const enumerate = useCallback(async () => {
    if (!navigator.mediaDevices?.enumerateDevices) return;
    const devices = await navigator.mediaDevices.enumerateDevices();
    const audios: MediaDeviceInfo[] = [];
    const videos: MediaDeviceInfo[] = [];
    for (const d of devices) {
      if (!d.deviceId) continue;
      const info: MediaDeviceInfo = { deviceId: d.deviceId, label: d.label || "Device" };
      if (d.kind === "audioinput") audios.push(info);
      else if (d.kind === "videoinput") videos.push(info);
    }
    setAudioDevices(audios);
    setVideoDevices(videos);
  }, []);

  const acquireStream = useCallback(
    async (audioId?: string | null, videoId?: string | null) => {
      if (!navigator.mediaDevices?.getUserMedia) {
        throw new MediaPermissionException("UNKNOWN", "Browser does not support getUserMedia");
      }
      const constraints: MediaStreamConstraints = {
        audio:
          audioId !== undefined
            ? audioId
              ? { deviceId: { exact: audioId } }
              : false
            : DEFAULT_MEDIA_CONSTRAINTS.audio,
        video:
          videoId !== undefined
            ? videoId
              ? { deviceId: { exact: videoId } }
              : false
              : DEFAULT_MEDIA_CONSTRAINTS.video,
      };

      try {
        const next = await navigator.mediaDevices.getUserMedia(constraints);
        stopStream();
        streamRef.current = next;
        setStream(next);
        setPermissionState("granted");
        setErrorMessage(null);

        const audioTrack = next.getAudioTracks()[0];
        const videoTrack = next.getVideoTracks()[0];
        const settings = next.getVideoTracks()[0]?.getSettings?.();
        const resolvedAudioId = audioTrack?.getSettings().deviceId ?? null;
        const resolvedVideoId = videoTrack?.getSettings().deviceId ?? settings?.deviceId ?? null;
        setCurrentAudioId(resolvedAudioId);
        setCurrentVideoId(resolvedVideoId);
        currentAudioIdRef.current = resolvedAudioId;
        currentVideoIdRef.current = resolvedVideoId;
        await enumerate();
        return next;
      } catch (err) {
        const name = (err as { name?: string })?.name;
        const message = readErrorMessage(err);
        if (message.toLowerCase().includes("permissions policy") || message.toLowerCase().includes("not allowed in this document")) {
          setPermissionState("denied");
          throw new MediaPermissionException("POLICY_BLOCKED", message);
        }
        if (name === "NotAllowedError" || name === "PermissionDeniedError") {
          setPermissionState("denied");
          throw new MediaPermissionException("PERMISSION_DENIED", readPermissionDeniedMessage(err));
        }
        if (name === "NotFoundError" || name === "OverconstrainedError") {
          setPermissionState("error");
          throw new MediaPermissionException("DEVICE_NOT_FOUND", message);
        }
        setPermissionState("error");
        setErrorMessage(message);
        throw new MediaPermissionException("UNKNOWN", message);
      }
    },
    [enumerate, stopStream],
  );

  const start = useCallback(async () => {
    await acquireStream();
  }, [acquireStream]);

  const setAudio = useCallback(
    async (deviceId: string) => {
      setSelectingDevice(true);
      try {
        await acquireStream(deviceId, currentVideoIdRef.current);
      } finally {
        setSelectingDevice(false);
      }
    },
    [acquireStream],
  );

  const setVideo = useCallback(
    async (deviceId: string) => {
      setSelectingDevice(true);
      try {
        await acquireStream(currentAudioIdRef.current, deviceId);
      } finally {
        setSelectingDevice(false);
      }
    },
    [acquireStream],
  );

  const stop = useCallback(() => {
    stopStream();
    setCurrentAudioId(null);
    setCurrentVideoId(null);
    currentAudioIdRef.current = null;
    currentVideoIdRef.current = null;
  }, [stopStream]);

  useEffect(() => {
    const handler = () => {
      enumerate().catch(() => {});
    };
    if (navigator.mediaDevices?.addEventListener) {
      navigator.mediaDevices.addEventListener("devicechange", handler);
      return () => navigator.mediaDevices.removeEventListener("devicechange", handler);
    }
    return undefined;
  }, [enumerate]);

  useEffect(() => {
    return () => {
      stopStream();
    };
  }, [stopStream]);

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
    stop,
  };
}
