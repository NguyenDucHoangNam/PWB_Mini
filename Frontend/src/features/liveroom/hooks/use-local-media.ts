"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
  AUDIO_CONSTRAINTS,
  VIDEO_CONSTRAINTS,
  classifyMediaError,
  mediaDevicesAvailable,
  type MediaErrorKind,
} from "../lib/media-constraints";

export interface LocalMediaState {
  stream: MediaStream | null;
  audioTrack: MediaStreamTrack | null;
  videoTrack: MediaStreamTrack | null;
  cameraOn: boolean;
  micOn: boolean;
  requesting: boolean;
  error: MediaErrorKind | null;
  enableCamera: () => Promise<boolean>;
  disableCamera: () => void;
  enableMic: () => Promise<boolean>;
  disableMic: () => void;
  setMicEnabled: (enabled: boolean) => void;
  stopAll: () => void;
}


export function useLocalMedia(): LocalMediaState {
  const streamRef = useRef<MediaStream | null>(null);
  const audioTrackRef = useRef<MediaStreamTrack | null>(null);
  const [stream, setStream] = useState<MediaStream | null>(null);
  const [audioTrack, setAudioTrack] = useState<MediaStreamTrack | null>(null);
  const [videoTrack, setVideoTrack] = useState<MediaStreamTrack | null>(null);
  const [micOn, setMicOn] = useState(false);
  const [requesting, setRequesting] = useState(false);
  const [error, setError] = useState<MediaErrorKind | null>(null);
  const mountedRef = useRef(true);

  useEffect(() => {
    mountedRef.current = true;
    if (!streamRef.current) {
      streamRef.current = new MediaStream();
      setStream(streamRef.current);
    }
    return () => {
      mountedRef.current = false;
    };
  }, []);

  const swapTrack = useCallback(
    (previous: MediaStreamTrack | null, next: MediaStreamTrack | null) => {
      const container = streamRef.current;
      if (previous) {
        previous.stop();
        container?.removeTrack(previous);
      }
      if (next) container?.addTrack(next);
    },
    [],
  );

  const enableCamera = useCallback(async () => {
    if (!mediaDevicesAvailable()) {
      setError("unsupported");
      return false;
    }
    setRequesting(true);
    try {
      const media = await navigator.mediaDevices.getUserMedia({ video: VIDEO_CONSTRAINTS });
      const track = media.getVideoTracks()[0] ?? null;
      if (!mountedRef.current) {
        track?.stop();
        return false;
      }
      swapTrack(videoTrack, track);
      setVideoTrack(track);
      setError(null);
      return Boolean(track);
    } catch (err) {
      if (mountedRef.current) setError(classifyMediaError(err));
      return false;
    } finally {
      if (mountedRef.current) setRequesting(false);
    }
  }, [swapTrack, videoTrack]);

  const disableCamera = useCallback(() => {
    swapTrack(videoTrack, null);
    setVideoTrack(null);
  }, [swapTrack, videoTrack]);

  const setMicEnabled = useCallback((enabled: boolean) => setMicOn(enabled), []);


  useEffect(() => {
    const track = audioTrackRef.current;
    if (track) track.enabled = micOn;
  }, [audioTrack, micOn]);

  const enableMic = useCallback(async () => {
    if (!mediaDevicesAvailable()) {
      setError("unsupported");
      return false;
    }
    if (audioTrack) {
      setMicEnabled(true);
      return true;
    }
    setRequesting(true);
    try {
      const media = await navigator.mediaDevices.getUserMedia({ audio: AUDIO_CONSTRAINTS });
      const track = media.getAudioTracks()[0] ?? null;
      if (!mountedRef.current) {
        track?.stop();
        return false;
      }
      swapTrack(null, track);
      audioTrackRef.current = track;
      setAudioTrack(track);
      setMicOn(Boolean(track));
      setError(null);
      return Boolean(track);
    } catch (err) {
      if (mountedRef.current) setError(classifyMediaError(err));
      return false;
    } finally {
      if (mountedRef.current) setRequesting(false);
    }
  }, [audioTrack, setMicEnabled, swapTrack]);

  const disableMic = useCallback(() => setMicEnabled(false), [setMicEnabled]);

  const stopAll = useCallback(() => {
    streamRef.current?.getTracks().forEach((track) => {
      track.stop();
      streamRef.current?.removeTrack(track);
    });
    audioTrackRef.current = null;
    setVideoTrack(null);
    setAudioTrack(null);
    setMicOn(false);
  }, []);

  useEffect(
    () => () => {
      streamRef.current?.getTracks().forEach((track) => track.stop());
    },
    [],
  );

  return {
    stream,
    audioTrack,
    videoTrack,
    cameraOn: Boolean(videoTrack),
    micOn,
    requesting,
    error,
    enableCamera,
    disableCamera,
    enableMic,
    disableMic,
    setMicEnabled,
    stopAll,
  };
}