"use client";

import { useEffect, useRef } from "react";
import { useLiveRoomMediaStore } from "../stores/use-live-room-media-store";

const SPEAKING_THRESHOLD_DB = -50;
const HOLD_MS = 500;
const POLL_INTERVAL_MS = 100;

function createAudioAnalyser(
  stream: MediaStream,
): { analyser: AnalyserNode; ctx: AudioContext; source: MediaStreamAudioSourceNode } | null {
  const audioTracks = stream.getAudioTracks();
  if (audioTracks.length === 0) return null;

  const ctx = new AudioContext();
  const analyser = ctx.createAnalyser();
  analyser.fftSize = 256;
  analyser.smoothingTimeConstant = 0.8;

  const source = ctx.createMediaStreamSource(stream);
  source.connect(analyser);

  return { analyser, ctx, source };
}

function measureDb(analyser: AnalyserNode, buffer: Uint8Array<ArrayBuffer>): number {
  analyser.getByteFrequencyData(buffer);
  let sum = 0;
  for (let i = 0; i < buffer.length; i++) {
    sum += buffer[i];
  }
  const average = sum / buffer.length;
  return average > 0 ? 20 * Math.log10(average / 255) : -100;
}

export function useActiveSpeaker(localUserId: string): void {
  const addSpeaker = useLiveRoomMediaStore((s) => s.addSpeaker);
  const removeSpeaker = useLiveRoomMediaStore((s) => s.removeSpeaker);

  useEffect(() => {
    const analysers = new Map<
      string,
      { analyser: AnalyserNode; ctx: AudioContext; source: MediaStreamAudioSourceNode; buffer: Uint8Array<ArrayBuffer>; lastSpokeAt: number }
    >();

    const attach = (userId: string, stream: MediaStream) => {
      if (analysers.has(userId)) return;
      const result = createAudioAnalyser(stream);
      if (!result) return;
      analysers.set(userId, {
        ...result,
        buffer: new Uint8Array(result.analyser.frequencyBinCount),
        lastSpokeAt: 0,
      });
    };

    const detach = (userId: string) => {
      const entry = analysers.get(userId);
      if (!entry) return;
      entry.source.disconnect();
      entry.analyser.disconnect();
      if (entry.ctx.state !== "closed") {
        void entry.ctx.close();
      }
      analysers.delete(userId);
      removeSpeaker(userId);
    };

    const syncStreams = () => {
      const state = useLiveRoomMediaStore.getState();
      const activeIds = new Set<string>();

      if (state.localStream && state.localStream.getAudioTracks().length > 0) {
        activeIds.add(localUserId);
        attach(localUserId, state.localStream);
      }

      for (const peer of state.remotePeers) {
        if (peer.stream && peer.stream.getAudioTracks().length > 0) {
          activeIds.add(peer.userId);
          attach(peer.userId, peer.stream);
        }
      }

      for (const userId of analysers.keys()) {
        if (!activeIds.has(userId)) {
          detach(userId);
        }
      }
    };

    syncStreams();

    const interval = setInterval(() => {
      syncStreams();

      const now = Date.now();
      const state = useLiveRoomMediaStore.getState();
      for (const [userId, entry] of analysers) {
        const isMuted =
          userId === localUserId
            ? state.micMuted
            : (state.remoteMediaStates[userId]?.micMuted ?? false);

        if (isMuted) {
          removeSpeaker(userId);
          continue;
        }

        const db = measureDb(entry.analyser, entry.buffer);
        if (db > SPEAKING_THRESHOLD_DB) {
          entry.lastSpokeAt = now;
          addSpeaker(userId);
        } else if (now - entry.lastSpokeAt > HOLD_MS) {
          removeSpeaker(userId);
        }
      }
    }, POLL_INTERVAL_MS);

    return () => {
      clearInterval(interval);
      for (const userId of [...analysers.keys()]) {
        detach(userId);
      }
    };
  }, [localUserId, addSpeaker, removeSpeaker]);
}
