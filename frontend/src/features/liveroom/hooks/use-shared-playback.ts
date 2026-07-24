"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import {
  getSharedStreamUrl,
  sharedStreamKey,
} from "../api/playback";
import {
  sendPlaybackLoop,
  sendPlaybackPause,
  sendPlaybackPlay,
  sendPlaybackRate,
  sendPlaybackSeek,
  sendPlaybackShuffle,
  sendPlaybackStateRequest,
  subscribeRoomPlayback,
  subscribeUserPlaybackState,
  type Subscription,
} from "../api/ws";
import { playbackKey } from "../api/playback";
import type {
  PlaybackSnapshot,
  PlaybackStatus,
  PlaybackWsEvent,
  SongPlaybackSummary,
} from "../types";

interface ComputedPlayback {
  snapshot: PlaybackSnapshot | null;
  isPlaying: boolean;
  positionSeconds: number;
  status: PlaybackStatus | null;
  song: SongPlaybackSummary | null;
}

const EMPTY_COMPUTED: ComputedPlayback = {
  snapshot: null,
  isPlaying: false,
  positionSeconds: 0,
  status: null,
  song: null,
};

const DRIFT_THRESHOLD_MS = 1500;
const COMMAND_DEBOUNCE_MS = 250;

function normalizeSnapshot(event: PlaybackWsEvent): PlaybackSnapshot | null {
  if (!event) {
    return null;
  }
  const song = event.song
    ? {
        songId: event.song.songId ?? "",
        ownerUserId: event.song.ownerUserId ?? "",
        title: event.song.title ?? "",
        artist: event.song.artist ?? null,
        album: event.song.album ?? null,
        durationSeconds: event.song.durationSeconds ?? null,
        format: event.song.format ?? "",
        processable: Boolean(event.song.processable),
      }
    : null;
  return {
    roomCode: event.roomCode,
    status: event.status,
    positionSeconds: event.positionSeconds ?? 0,
    effectiveAt: event.effectiveAt ?? "",
    version: event.version ?? 0,
    changedByUserId: event.changedByUserId ?? null,
    changedAt: event.changedAt ?? "",
    empty: Boolean(event.empty),
    song,
    playbackRate: event.playbackRate ?? "1.00",
    loopMode: event.loopMode === "ONE" ? "ONE" : "OFF",
    shuffleEnabled: Boolean(event.shuffleEnabled),
  };
}

export function useSharedPlayback({
  roomCode,
  enabled = true,
}: {
  roomCode: string;
  enabled?: boolean;
}) {
  const queryClient = useQueryClient();
  const [snapshot, setSnapshot] = useState<PlaybackSnapshot | null>(null);
  const [nowMs, setNowMs] = useState<number>(0);
  const lastAppliedVersionRef = useRef<number>(-1);
  const lastAppliedSnapshotRef = useRef<PlaybackSnapshot | null>(null);
  const lastCommandAtRef = useRef<number>(0);

  const applyEvent = useCallback((raw: PlaybackWsEvent) => {
    const normalized = normalizeSnapshot(raw);
    if (!normalized) {
      return;
    }
    if (normalized.version <= lastAppliedVersionRef.current) {
      return;
    }
    lastAppliedVersionRef.current = normalized.version;
    lastAppliedSnapshotRef.current = normalized;
    setSnapshot(normalized);
  }, []);

  useEffect(() => {
    if (!enabled || !roomCode) {
      return;
    }
    const subs: Subscription[] = [];
    subs.push(subscribeRoomPlayback(roomCode, applyEvent));
    subs.push(subscribeUserPlaybackState(roomCode, applyEvent));
    sendPlaybackStateRequest(roomCode);

    const fromQuery = queryClient.getQueryData<{
      success: boolean;
      data?: PlaybackSnapshot | null;
    }>(playbackKey(roomCode));
    if (fromQuery?.success && fromQuery.data) {
      applyEvent({
        type: "PLAYBACK_STATE",
        roomCode: fromQuery.data.roomCode,
        status: fromQuery.data.status,
        positionSeconds: fromQuery.data.positionSeconds,
        effectiveAt: fromQuery.data.effectiveAt,
        version: fromQuery.data.version,
        empty: fromQuery.data.empty,
        song: fromQuery.data.song
          ? {
              songId: fromQuery.data.song.songId,
              ownerUserId: fromQuery.data.song.ownerUserId,
              title: fromQuery.data.song.title,
              artist: fromQuery.data.song.artist,
              album: fromQuery.data.song.album,
              durationSeconds: fromQuery.data.song.durationSeconds,
              format: fromQuery.data.song.format,
              processable: fromQuery.data.song.processable,
            }
          : null,
        changedByUserId: fromQuery.data.changedByUserId,
        changedAt: fromQuery.data.changedAt,
        timestamp: "",
        playbackRate: fromQuery.data.playbackRate ?? "1.00",
        loopMode: fromQuery.data.loopMode ?? "OFF",
        shuffleEnabled: Boolean(fromQuery.data.shuffleEnabled),
      });
    }

    return () => {
      for (const sub of subs) {
        sub.unsubscribe();
      }
    };
  }, [applyEvent, enabled, queryClient, roomCode]);

  useEffect(() => {
    if (!enabled) {
      lastAppliedVersionRef.current = -1;
      lastAppliedSnapshotRef.current = null;
      return;
    }
    setSnapshot(null);
  }, [enabled]);

  useEffect(() => {
    if (!enabled) {
      return;
    }
    setNowMs(Date.now());
    const timer = setInterval(() => setNowMs(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [enabled]);

  useEffect(() => {
    if (typeof document === "undefined") {
      return;
    }
    const onVisible = () => {
      if (document.visibilityState === "visible") {
        setNowMs(Date.now());
        if (roomCode) {
          sendPlaybackStateRequest(roomCode);
        }
      }
    };
    document.addEventListener("visibilitychange", onVisible);
    return () => document.removeEventListener("visibilitychange", onVisible);
  }, [roomCode]);

  const computed: ComputedPlayback = useMemo(() => {
    if (!snapshot) {
      return EMPTY_COMPUTED;
    }
    if (snapshot.status === "PLAYING" && !snapshot.empty && nowMs > 0) {
      const effective = new Date(snapshot.effectiveAt).getTime();
      const elapsed = Number.isFinite(effective)
        ? Math.max(0, Math.floor((nowMs - effective) / 1000))
        : 0;
      return {
        snapshot,
        isPlaying: true,
        positionSeconds: snapshot.positionSeconds + elapsed,
        status: snapshot.status,
        song: snapshot.song,
      };
    }
    return {
      snapshot,
      isPlaying: false,
      positionSeconds: snapshot.positionSeconds,
      status: snapshot.status,
      song: snapshot.song,
    };
  }, [nowMs, snapshot]);

  const driftMs = useMemo(() => {
    if (!snapshot || !snapshot.effectiveAt || snapshot.empty || nowMs === 0) {
      return 0;
    }
    const effective = new Date(snapshot.effectiveAt).getTime();
    if (!Number.isFinite(effective)) {
      return 0;
    }
    return Math.abs(nowMs - effective);
  }, [nowMs, snapshot]);

  const needsReconcile = driftMs > DRIFT_THRESHOLD_MS;

  const sendWithDebounce = useCallback(
    (action: "play" | "pause") => {
      if (!roomCode) {
        return;
      }
      const now = Date.now();
      if (now - lastCommandAtRef.current < COMMAND_DEBOUNCE_MS) {
        return;
      }
      lastCommandAtRef.current = now;
      if (action === "play") {
        sendPlaybackPlay(roomCode);
      } else {
        sendPlaybackPause(roomCode);
      }
    },
    [roomCode],
  );

  const play = useCallback(() => sendWithDebounce("play"), [sendWithDebounce]);
  const pause = useCallback(() => sendWithDebounce("pause"), [sendWithDebounce]);

  const seek = useCallback(
    (direction: 1 | -1) => {
      if (!roomCode) {
        return;
      }
      sendPlaybackSeek(roomCode, direction);
    },
    [roomCode],
  );

  const setRate = useCallback(
    (rate: string) => {
      if (!roomCode) {
        return;
      }
      sendPlaybackRate(roomCode, rate);
    },
    [roomCode],
  );

  const setLoop = useCallback(
    (mode: "OFF" | "ONE") => {
      if (!roomCode) {
        return;
      }
      sendPlaybackLoop(roomCode, mode);
    },
    [roomCode],
  );

  const setShuffle = useCallback(
    (enabled: boolean) => {
      if (!roomCode) {
        return;
      }
      sendPlaybackShuffle(roomCode, enabled);
    },
    [roomCode],
  );

  const reconcile = useCallback(() => {
    if (!roomCode) {
      return;
    }
    sendPlaybackStateRequest(roomCode);
  }, [roomCode]);

  const songId = computed.song?.songId;
  const streamQueryKey = useMemo(
    () => (songId ? sharedStreamKey(roomCode, songId) : null),
    [roomCode, songId],
  );

  const streamFetcher = useCallback(() => {
    if (!songId) {
      throw new Error("Cannot fetch stream without songId");
    }
    return getSharedStreamUrl({ roomCode, songId });
  }, [roomCode, songId]);

  return {
    snapshot: computed.snapshot,
    isPlaying: computed.isPlaying,
    positionSeconds: computed.positionSeconds,
    status: computed.status,
    song: computed.song,
    playbackRate: snapshot?.playbackRate ?? "1.00",
    loopMode: snapshot?.loopMode ?? "OFF",
    shuffleEnabled: snapshot?.shuffleEnabled ?? false,
    play,
    pause,
    seek,
    setRate,
    setLoop,
    setShuffle,
    reconcile,
    needsReconcile,
    streamQueryKey,
    streamFetcher,
  };
}