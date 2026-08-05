"use client";

import { useEffect, useState } from "react";

export const WAVEFORM_BAR_COUNT = 180;




const cache = new Map<string, number[]>();
const inFlight = new Map<string, Promise<number[] | null>>();

let sharedContext: AudioContext | null = null;

function audioContext(): AudioContext | null {
  if (typeof window === "undefined") return null;
  const Ctor = window.AudioContext;
  if (!Ctor) return null;
  if (!sharedContext) sharedContext = new Ctor();
  return sharedContext;
}

function reduceToPeaks(buffer: AudioBuffer): number[] {
  const channel = buffer.getChannelData(0);
  const step = Math.max(1, Math.floor(channel.length / WAVEFORM_BAR_COUNT));
  const peaks: number[] = [];
  let loudest = 0;

  for (let bar = 0; bar < WAVEFORM_BAR_COUNT; bar += 1) {
    const start = bar * step;
    const end = Math.min(start + step, channel.length);
    let sum = 0;
    for (let index = start; index < end; index += 1) {
      sum += channel[index] * channel[index];
    }


    const rms = Math.sqrt(sum / Math.max(1, end - start));
    peaks.push(rms);
    if (rms > loudest) loudest = rms;
  }

  return loudest > 0 ? peaks.map((peak) => peak / loudest) : peaks;
}

async function decodePeaks(url: string): Promise<number[] | null> {
  const context = audioContext();
  if (!context) return null;
  try {
    const response = await fetch(url);
    if (!response.ok) return null;
    const decoded = await context.decodeAudioData(await response.arrayBuffer());
    return reduceToPeaks(decoded);
  } catch {
    return null;
  }
}

function loadPeaks(songId: string, url: string): Promise<number[] | null> {
  const running = inFlight.get(songId);
  if (running) return running;

  const task = decodePeaks(url).then((peaks) => {
    if (peaks) cache.set(songId, peaks);
    inFlight.delete(songId);
    return peaks;
  });
  inFlight.set(songId, task);
  return task;
}

export type WaveformState =
  | { status: "idle" | "loading" | "unavailable"; peaks: null }
  | { status: "ready"; peaks: number[] };




export function useWaveformPeaks(songId: string | null, url: string | null): WaveformState {
  const [settled, setSettled] = useState<{ songId: string; peaks: number[] | null } | null>(null);

  useEffect(() => {
    if (!songId || !url || cache.has(songId)) return;
    let cancelled = false;
    void loadPeaks(songId, url).then((peaks) => {
      if (!cancelled) setSettled({ songId, peaks });
    });
    return () => {
      cancelled = true;
    };
  }, [songId, url]);

  if (!songId) return { status: "idle", peaks: null };

  const cached = cache.get(songId);
  if (cached) return { status: "ready", peaks: cached };
  if (settled?.songId === songId) {
    return settled.peaks
      ? { status: "ready", peaks: settled.peaks }
      : { status: "unavailable", peaks: null };
  }
  return { status: url ? "loading" : "idle", peaks: null };
}