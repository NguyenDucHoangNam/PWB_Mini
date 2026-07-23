"use client";

import {
  MediaPermissionException,
  type MediaPermissionError,
  useMediaSessionStore,
} from "../stores/use-media-session-store";
import type { MediaDeviceInfo } from "../types";

const DEFAULT_MEDIA_CONSTRAINTS: MediaStreamConstraints = {
  audio: true,
  video: true,
};

let cameraToggleLock: Promise<void> = Promise.resolve();

function serializeCameraOp<T>(fn: () => Promise<T>): Promise<T> {
  const next = cameraToggleLock.then(fn, fn);
  cameraToggleLock = next.then(() => {}, () => {});
  return next;
}

let lastVideoDeviceId: string | null = null;

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

function isPolicyBlockedMessage(message: string): boolean {
  const lower = message.toLowerCase();
  return lower.includes("permissions policy") || lower.includes("not allowed in this document");
}

async function enumerateDevices(): Promise<{ audios: MediaDeviceInfo[]; videos: MediaDeviceInfo[] }> {
  if (!navigator.mediaDevices?.enumerateDevices) {
    return { audios: [], videos: [] };
  }
  const devices = await navigator.mediaDevices.enumerateDevices();
  const audios: MediaDeviceInfo[] = [];
  const videos: MediaDeviceInfo[] = [];
  for (const d of devices) {
    if (!d.deviceId) continue;
    const info: MediaDeviceInfo = { deviceId: d.deviceId, label: d.label || "Device" };
    if (d.kind === "audioinput") audios.push(info);
    else if (d.kind === "videoinput") videos.push(info);
  }
  return { audios, videos };
}

function releaseExistingStream(stream: MediaStream | null): void {
  if (!stream) return;
  for (const track of stream.getTracks()) {
    try {
      track.stop();
    } catch {
      /* ignore - track may already be ended */
    }
  }
}

function appendTrackSafely(stream: MediaStream, track: MediaStreamTrack): void {
  if (stream.getTrackById(track.id)) return;
  stream.addTrack(track);
}

function detachVideoTracks(stream: MediaStream): void {
  for (const track of stream.getVideoTracks()) {
    try {
      track.stop();
    } catch {
      /* ignore */
    }
    try {
      stream.removeTrack(track);
    } catch {
      /* ignore */
    }
  }
}

async function acquireAudioOnly(deviceId: string | null): Promise<MediaStreamTrack | null> {
  if (!navigator.mediaDevices?.getUserMedia) {
    throw new MediaPermissionException("UNKNOWN", "Browser does not support getUserMedia");
  }
  const constraints: MediaStreamConstraints = {
    audio: deviceId ? { deviceId: { exact: deviceId } } : DEFAULT_MEDIA_CONSTRAINTS.audio,
    video: false,
  };
  const stream = await navigator.mediaDevices.getUserMedia(constraints);
  const audioTrack = stream.getAudioTracks()[0] ?? null;
  for (const track of stream.getTracks()) {
    if (track !== audioTrack) {
      try {
        track.stop();
      } catch {
        /* ignore */
      }
    }
  }
  return audioTrack;
}

async function acquireVideoOnly(deviceId: string | null): Promise<MediaStreamTrack> {
  if (!navigator.mediaDevices?.getUserMedia) {
    throw new MediaPermissionException("UNKNOWN", "Browser does not support getUserMedia");
  }
  const constraints: MediaStreamConstraints = {
    audio: false,
    video: deviceId ? { deviceId: { exact: deviceId } } : DEFAULT_MEDIA_CONSTRAINTS.video,
  };
  const stream = await navigator.mediaDevices.getUserMedia(constraints);
  const videoTrack = stream.getVideoTracks()[0];
  if (!videoTrack) {
    for (const track of stream.getTracks()) {
      try {
        track.stop();
      } catch {
        /* ignore */
      }
    }
    throw new MediaPermissionException("DEVICE_NOT_FOUND", "No video track available");
  }
  for (const track of stream.getTracks()) {
    if (track !== videoTrack) {
      try {
        track.stop();
      } catch {
        /* ignore */
      }
    }
  }
  return videoTrack;
}

function toPermissionException(err: unknown): MediaPermissionException {
  const name = (err as { name?: string })?.name;
  const message = readErrorMessage(err);
  if (isPolicyBlockedMessage(message)) {
    return new MediaPermissionException("POLICY_BLOCKED", message);
  }
  if (name === "NotAllowedError" || name === "PermissionDeniedError") {
    return new MediaPermissionException("PERMISSION_DENIED", readPermissionDeniedMessage(err));
  }
  if (name === "NotFoundError" || name === "OverconstrainedError") {
    return new MediaPermissionException("DEVICE_NOT_FOUND", message);
  }
  return new MediaPermissionException("UNKNOWN", message);
}

export const mediaSessionController = {
  async start(): Promise<MediaStream> {
    const store = useMediaSessionStore.getState();
    if (!navigator.mediaDevices?.getUserMedia) {
      throw new MediaPermissionException("UNKNOWN", "Browser does not support getUserMedia");
    }
    const current = store.stream;
    const audioId = store.currentAudioId;
    const videoId = store.currentVideoId;
    const constraints: MediaStreamConstraints = {
      audio:
        audioId !== null
          ? audioId
            ? { deviceId: { exact: audioId } }
            : false
          : DEFAULT_MEDIA_CONSTRAINTS.audio,
      video:
        videoId !== null
          ? videoId
            ? { deviceId: { exact: videoId } }
            : false
          : DEFAULT_MEDIA_CONSTRAINTS.video,
    };
    try {
      const next = await navigator.mediaDevices.getUserMedia(constraints);
      releaseExistingStream(current);
      store.setStream(next);
      store.setPermissionState("granted");
      store.setErrorMessage(null);
      const audioTrack = next.getAudioTracks()[0];
      const videoTrack = next.getVideoTracks()[0];
      store.setCurrentAudioId(audioTrack?.getSettings().deviceId ?? null);
      store.setCurrentVideoId(videoTrack?.getSettings().deviceId ?? null);
      const { audios, videos } = await enumerateDevices();
      store.setDevices(audios, videos);
      return next;
    } catch (err) {
      const ex = toPermissionException(err);
      if (ex.kind === "PERMISSION_DENIED" || ex.kind === "POLICY_BLOCKED") {
        store.setPermissionState("denied");
      } else {
        store.setPermissionState("error");
        store.setErrorMessage(ex.message);
      }
      throw ex;
    }
  },

  async setAudio(deviceId: string): Promise<MediaStream> {
    const store = useMediaSessionStore.getState();
    store.setSelectingDevice(true);
    try {
      if (!navigator.mediaDevices?.getUserMedia) {
        throw new MediaPermissionException("UNKNOWN", "Browser does not support getUserMedia");
      }
      const current = store.stream;
      const constraints: MediaStreamConstraints = {
        audio: { deviceId: { exact: deviceId } },
        video: false,
      };
      const audioStream = await navigator.mediaDevices.getUserMedia(constraints);
      const audioTrack = audioStream.getAudioTracks()[0];
      for (const track of audioStream.getTracks()) {
        if (track !== audioTrack) {
          try {
            track.stop();
          } catch {
            /* ignore */
          }
        }
      }
      if (!audioTrack) {
        throw new MediaPermissionException("DEVICE_NOT_FOUND", "No audio track available");
      }
      if (current) {
        for (const track of current.getAudioTracks()) {
          try {
            current.removeTrack(track);
          } catch {
            /* ignore */
          }
          try {
            track.stop();
          } catch {
            /* ignore */
          }
        }
        appendTrackSafely(current, audioTrack);
      }
      store.setCurrentAudioId(audioTrack.getSettings().deviceId ?? deviceId);
      store.setPermissionState("granted");
      store.setErrorMessage(null);
      const { audios, videos } = await enumerateDevices();
      store.setDevices(audios, videos);
      return current ?? new MediaStream([audioTrack]);
    } catch (err) {
      const ex = toPermissionException(err);
      store.setPermissionState(ex.kind === "PERMISSION_DENIED" || ex.kind === "POLICY_BLOCKED" ? "denied" : "error");
      store.setErrorMessage(ex.message);
      throw ex;
    } finally {
      store.setSelectingDevice(false);
    }
  },

  async setVideo(deviceId: string): Promise<MediaStream> {
    const store = useMediaSessionStore.getState();
    store.setSelectingDevice(true);
    try {
      const current = store.stream;
      const videoTrack = await acquireVideoOnly(deviceId);
      if (current) {
        detachVideoTracks(current);
        appendTrackSafely(current, videoTrack);
      }
      store.setCurrentVideoId(videoTrack.getSettings().deviceId ?? deviceId);
      store.setPermissionState("granted");
      store.setErrorMessage(null);
      const { audios, videos } = await enumerateDevices();
      store.setDevices(audios, videos);
      return current ?? new MediaStream([videoTrack]);
    } catch (err) {
      const ex = toPermissionException(err);
      store.setPermissionState(ex.kind === "PERMISSION_DENIED" || ex.kind === "POLICY_BLOCKED" ? "denied" : "error");
      store.setErrorMessage(ex.message);
      throw ex;
    } finally {
      store.setSelectingDevice(false);
    }
  },

  async enableCamera(): Promise<MediaStreamTrack | null> {
    return serializeCameraOp(async () => {
      const store = useMediaSessionStore.getState();
      store.setCameraBusy(false, true);
      try {
        const current = store.stream;
        const deviceId = store.currentVideoId ?? lastVideoDeviceId;
        const videoTrack = await acquireVideoOnly(deviceId);
        if (current) {
          detachVideoTracks(current);
          appendTrackSafely(current, videoTrack);
        } else {
          const fallbackStream = new MediaStream([videoTrack]);
          store.setStream(fallbackStream);
        }
        const resolvedId = videoTrack.getSettings().deviceId ?? deviceId ?? null;
        store.setCurrentVideoId(resolvedId);
        lastVideoDeviceId = resolvedId;
        store.setPermissionState("granted");
        store.setErrorMessage(null);
        store.bumpStreamRevision();
        const { audios, videos } = await enumerateDevices();
        store.setDevices(audios, videos);
        return videoTrack;
      } catch (err) {
        const ex = toPermissionException(err);
        store.setPermissionState(ex.kind === "PERMISSION_DENIED" || ex.kind === "POLICY_BLOCKED" ? "denied" : "error");
        store.setErrorMessage(ex.message);
        throw ex;
      } finally {
        store.setCameraBusy(false, false);
      }
    });
  },

  async disableCamera(): Promise<void> {
    return serializeCameraOp(async () => {
      const store = useMediaSessionStore.getState();
      store.setCameraBusy(true, false);
      try {
        const current = store.stream;
        const currentVideoId = store.currentVideoId;
        if (currentVideoId) {
          lastVideoDeviceId = currentVideoId;
        }
        if (current) {
          detachVideoTracks(current);
        }
        store.setCurrentVideoId(null);
        store.bumpStreamRevision();
        const { audios, videos } = await enumerateDevices();
        store.setDevices(audios, videos);
      } finally {
        store.setCameraBusy(false, false);
      }
    });
  },

  async ensureAudio(): Promise<void> {
    const store = useMediaSessionStore.getState();
    const current = store.stream;
    if (current && current.getAudioTracks().length > 0) return;
    const audioTrack = await acquireAudioOnly(store.currentAudioId);
    if (!audioTrack) return;
    if (current) {
      appendTrackSafely(current, audioTrack);
    }
    store.setCurrentAudioId(audioTrack.getSettings().deviceId ?? store.currentAudioId ?? null);
  },

  stop(): void {
    const store = useMediaSessionStore.getState();
    releaseExistingStream(store.stream);
    store.setStream(null);
    store.setCurrentAudioId(null);
    store.setCurrentVideoId(null);
  },

  releaseForUnload(): void {
    const store = useMediaSessionStore.getState();
    if (store.stream) {
      releaseExistingStream(store.stream);
      store.setStream(null);
      store.setCurrentAudioId(null);
      store.setCurrentVideoId(null);
    }
  },

  async refreshDevices(): Promise<void> {
    const store = useMediaSessionStore.getState();
    const { audios, videos } = await enumerateDevices();
    store.setDevices(audios, videos);
  },
};

export type { MediaPermissionError };
