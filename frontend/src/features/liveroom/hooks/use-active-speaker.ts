"use client";

import { useEffect, useRef } from "react";
import { useLiveRoomMediaStore } from "../stores/use-live-room-media-store";

interface UseActiveSpeakerOptions {
  threshold?: number;
  smoothingTimeConstant?: number;
}

export function useActiveSpeaker(
  localUserId: string,
  options: UseActiveSpeakerOptions = {},
): void {
  const { threshold = -50, smoothingTimeConstant = 0.8 } = options;
  const setSpeakingUsers = useLiveRoomMediaStore((state) => state.setSpeakingUsers);
  const localStream = useLiveRoomMediaStore((state) => state.localStream);
  const remotePeers = useLiveRoomMediaStore((state) => state.remotePeers);
  const analyserRef = useRef<AnalyserNode | null>(null);
  const audioContextRef = useRef<AudioContext | null>(null);
  const sourceRef = useRef<MediaStreamAudioSourceNode | null>(null);
  const animationRef = useRef<number | null>(null);
  const lastSpeakingRef = useRef<Map<string, number>>(new Map());

  useEffect(() => {
    if (!localStream || localStream.getAudioTracks().length === 0) return;

    const audioContext = new AudioContext();
    audioContextRef.current = audioContext;
    const analyser = audioContext.createAnalyser();
    analyser.fftSize = 256;
    analyser.smoothingTimeConstant = smoothingTimeConstant;
    analyserRef.current = analyser;

    const source = audioContext.createMediaStreamSource(localStream);
    source.connect(analyser);
    sourceRef.current = source;

    const dataArray = new Uint8Array(analyser.frequencyBinCount);

    const detect = () => {
      if (!analyser) return;
      analyser.getByteFrequencyData(dataArray);
      const average = dataArray.reduce((a, b) => a + b, 0) / dataArray.length;
      const db = average > 0 ? 20 * Math.log10(average / 255) : -100;
      const isSpeaking = db > threshold;

      const now = Date.now();
      const speakingUsers = new Set<string>();

      if (isSpeaking) {
        speakingUsers.add(localUserId);
        lastSpeakingRef.current.set(localUserId, now);
      }

      for (const peer of remotePeers) {
        const lastSpoke = lastSpeakingRef.current.get(peer.userId) ?? 0;
        if (now - lastSpoke < 1000) {
          speakingUsers.add(peer.userId);
        }
      }

      setSpeakingUsers(speakingUsers);
      animationRef.current = requestAnimationFrame(detect);
    };

    animationRef.current = requestAnimationFrame(detect);

    return () => {
      if (animationRef.current) {
        cancelAnimationFrame(animationRef.current);
      }
      source.disconnect();
      analyser.disconnect();
      if (audioContext.state !== "closed") {
        void audioContext.close();
      }
    };
  }, [localStream, localUserId, remotePeers, threshold, smoothingTimeConstant, setSpeakingUsers]);
}

export function useRemoteSpeakerDetection(
  peerUserId: string,
  stream: MediaStream,
): void {
  const setSpeakingUsers = useLiveRoomMediaStore((state) => state.setSpeakingUsers);
  const lastSpeakingRef = useRef<number>(0);

  useEffect(() => {
    if (!stream || stream.getAudioTracks().length === 0) return;

    const audioContext = new AudioContext();
    const analyser = audioContext.createAnalyser();
    analyser.fftSize = 256;
    analyser.smoothingTimeConstant = 0.8;

    const source = audioContext.createMediaStreamSource(stream);
    source.connect(analyser);

    const dataArray = new Uint8Array(analyser.frequencyBinCount);

    const detect = () => {
      analyser.getByteFrequencyData(dataArray);
      const average = dataArray.reduce((a, b) => a + b, 0) / dataArray.length;
      const db = average > 0 ? 20 * Math.log10(average / 255) : -100;
      const now = Date.now();

      if (db > -50) {
        lastSpeakingRef.current = now;
      }

      const speakingUsers = new Set<string>();
      if (now - lastSpeakingRef.current < 500) {
        speakingUsers.add(peerUserId);
      }
      setSpeakingUsers(speakingUsers);

      requestAnimationFrame(detect);
    };

    const animationId = requestAnimationFrame(detect);

    return () => {
      cancelAnimationFrame(animationId);
      source.disconnect();
      analyser.disconnect();
      if (audioContext.state !== "closed") {
        void audioContext.close();
      }
    };
  }, [stream, peerUserId, setSpeakingUsers]);
}
