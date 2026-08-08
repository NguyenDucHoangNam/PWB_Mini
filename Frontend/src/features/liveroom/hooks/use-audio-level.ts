"use client";

import { useEffect, useState } from "react";

const SAMPLE_INTERVAL_MS = 100;
const STEPS = 4;
const FULL_SCALE = 48;

let sharedContext: AudioContext | null = null;

function audioContext(): AudioContext | null {
  if (typeof window === "undefined") return null;
  const Ctor = window.AudioContext;
  if (!Ctor) return null;
  if (!sharedContext) sharedContext = new Ctor();
  return sharedContext;
}

export function useAudioLevel(stream: MediaStream | null): number {
  const [step, setStep] = useState(0);

  useEffect(() => {
    if (!stream) return;

    const context = audioContext();
    if (!context) return;

    let source: MediaStreamAudioSourceNode | null = null;
    let analyser: AnalyserNode | null = null;
    let samples: Uint8Array<ArrayBuffer> | null = null;
    let attached: MediaStreamTrack | null = null;

    const detach = () => {
      source?.disconnect();
      analyser?.disconnect();
      source = null;
      analyser = null;
      samples = null;
      attached = null;
    };


    const sync = () => {
      const track = stream.getAudioTracks()[0] ?? null;
      if (track === attached) return;
      detach();
      if (!track) return;
      try {
        source = context.createMediaStreamSource(new MediaStream([track]));
      } catch {
        return;
      }
      analyser = context.createAnalyser();
      analyser.fftSize = 512;
      analyser.smoothingTimeConstant = 0.5;
      source.connect(analyser);
      samples = new Uint8Array(new ArrayBuffer(analyser.fftSize));
      attached = track;
    };

    const timer = window.setInterval(() => {
      if (context.state !== "running") void context.resume().catch(() => undefined);
      sync();
      if (!analyser || !samples) {
        setStep(0);
        return;
      }
      analyser.getByteTimeDomainData(samples);
      let peak = 0;
      for (let index = 0; index < samples.length; index += 1) {
        const deviation = Math.abs(samples[index] - 128);
        if (deviation > peak) peak = deviation;
      }
      setStep(Math.min(STEPS, Math.round((peak / FULL_SCALE) * STEPS)));
    }, SAMPLE_INTERVAL_MS);

    return () => {
      window.clearInterval(timer);
      detach();
    };
  }, [stream]);

  return step;
}