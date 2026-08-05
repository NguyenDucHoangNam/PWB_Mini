"use client";

import { useEffect, useState } from "react";
import { serverNow } from "../lib/server-clock";
import { useLiveroomStore } from "../stores/use-liveroom-store";
import type { MusicStateData } from "../types/events";

const TICK_MS = 250;


export function positionAt(state: MusicStateData | null, nowMs: number): number {
  if (!state) return 0;
  if (state.status !== "PLAYING" || !state.startedAt) return state.positionSeconds;
  const elapsed = (nowMs - Date.parse(state.startedAt)) / 1000;
  const raw = state.positionSeconds + Math.max(0, elapsed);
  const duration = state.songDurationSeconds ?? Number.POSITIVE_INFINITY;
  return Math.min(duration, raw);
}

export function usePlaybackPosition(): number {
  const state = useLiveroomStore((store) => store.music.state);
  const [, setTick] = useState(0);
  const playing = state?.status === "PLAYING";

  useEffect(() => {
    if (!playing) return;
    const id = window.setInterval(() => setTick((value) => value + 1), TICK_MS);
    return () => window.clearInterval(id);
  }, [playing]);

  return positionAt(state, serverNow());
}