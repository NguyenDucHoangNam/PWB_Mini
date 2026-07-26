"use client";

import { useEffect, useState } from "react";

const SMOOTHING_FACTOR = 0.18;
const DECAY_THRESHOLD = 0.02;

export function useMicLevelMeter(stream: MediaStream | null, enabled: boolean): number {
  const [level, setLevel] = useState(0);

  useEffect(() => {
    if (!enabled || !stream || typeof window === "undefined") {
      setLevel(0);
      return;
    }

    const audioTrack = stream.getAudioTracks()[0];
    if (!audioTrack) {
      setLevel(0);
      return;
    }

    const AudioContextCtor: typeof AudioContext | undefined =
      window.AudioContext ??
      (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;

    if (!AudioContextCtor) {
      return;
    }

    let cancelled = false;
    const audioContext = new AudioContextCtor();
    const source = audioContext.createMediaStreamSource(stream);
    const analyser = audioContext.createAnalyser();
    analyser.fftSize = 256;
    analyser.smoothingTimeConstant = 0.4;
    source.connect(analyser);

    const dataArray = new Uint8Array(analyser.fftSize);
    let smoothed = 0;

    const sample = () => {
      if (cancelled) return;
      analyser.getByteTimeDomainData(dataArray);
      let sumSquares = 0;
      for (let i = 0; i < dataArray.length; i++) {
        const normalized = (dataArray[i] - 128) / 128;
        sumSquares += normalized * normalized;
      }
      const rms = Math.sqrt(sumSquares / dataArray.length);
      const target = Math.min(1, rms * 2.4);

      smoothed = smoothed + (target - smoothed) * SMOOTHING_FACTOR;
      if (Math.abs(smoothed - target) > DECAY_THRESHOLD) {
        smoothed = target;
      }
      setLevel((prev) => (Math.abs(prev - smoothed) > DECAY_THRESHOLD ? smoothed : prev));
    };

    const interval = window.setInterval(sample, 80);

    return () => {
      cancelled = true;
      window.clearInterval(interval);
      try {
        source.disconnect();
      } catch {
        /* ignore */
      }
      try {
        analyser.disconnect();
      } catch {
        /* ignore */
      }
      if (audioContext.state !== "closed") {
        audioContext.close().catch(() => {});
      }
      setLevel(0);
    };
  }, [stream, enabled]);

  return level;
}
