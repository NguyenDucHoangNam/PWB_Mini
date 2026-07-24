"use client";

import { useEffect, useRef } from "react";
import { useTranslations } from "next-intl";
import {
  Pause,
  Play,
  RefreshCw,
  Repeat,
  Shuffle,
  SkipBack,
  SkipForward,
  X,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { usePresignedUrl } from "@/features/voice/hooks/use-presigned-url";
import { useSharedPlayback } from "../hooks/use-shared-playback";
import type { PlaybackStatus, SongPlaybackSummary } from "../types";
import { toast } from "sonner";

const RATE_OPTIONS = ["1.00", "1.50", "2.00"] as const;
type PlaybackRateOption = (typeof RATE_OPTIONS)[number];

function isPlaybackRateOption(value: string): value is PlaybackRateOption {
  return (RATE_OPTIONS as readonly string[]).includes(value);
}

interface SharedPlaybackBarProps {
  roomCode: string;
  isHost: boolean;
  onChooseSong?: () => void;
}

function formatTime(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds < 0) {
    return "00:00";
  }
  const total = Math.floor(seconds);
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
}

function statusLabel(t: (k: string) => string, status: PlaybackStatus): string {
  switch (status) {
    case "PLAYING":
      return t("status.playing");
    case "PAUSED":
      return t("status.paused");
    case "ENDED":
      return t("status.ended");
    case "CLEARED":
      return t("status.cleared");
    default:
      return t("status.empty");
  }
}

export function SharedPlaybackBar({
  roomCode,
  isHost,
  onChooseSong,
}: SharedPlaybackBarProps) {
  const t = useTranslations("liveroom.playback");
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const lastVersionRef = useRef<number>(-1);

  const playback = useSharedPlayback({ roomCode, enabled: Boolean(roomCode) });
  const {
    snapshot,
    song,
    isPlaying,
    positionSeconds,
    status,
    playbackRate,
    loopMode,
    shuffleEnabled,
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
  } = playback;

  const audioRate: PlaybackRateOption = isPlaybackRateOption(playbackRate)
    ? playbackRate
    : "1.00";

  const streamEnabled = Boolean(song?.songId);
  const streamQuery = usePresignedUrl({
    fetcher: streamFetcher,
    enabled: streamEnabled,
    queryKey: streamQueryKey ?? ["shared-playback", "empty"],
  });
  const streamUrl = streamQuery.data?.data?.url ?? null;

  useEffect(() => {
    const audio = audioRef.current;
    if (!audio) {
      return;
    }
    if (!snapshot || snapshot.empty || !streamUrl) {
      audio.pause();
      return;
    }
    audio.src = streamUrl;
    audio.load();
  }, [snapshot?.empty, snapshot?.version, streamUrl]);

  useEffect(() => {
    const audio = audioRef.current;
    if (!audio || !snapshot || snapshot.empty) {
      return;
    }
    if (snapshot.version === lastVersionRef.current) {
      return;
    }
    lastVersionRef.current = snapshot.version;
    if (typeof audio.currentTime !== "number") {
      return;
    }
    const target =
      snapshot.status === "PLAYING"
        ? snapshot.positionSeconds
        : snapshot.positionSeconds;
    audio.currentTime = Math.max(0, target);
    audio.playbackRate = audioRate === "1.00" ? 1.0 : audioRate === "1.50" ? 1.5 : 2.0;
    if (snapshot.status === "PLAYING") {
      audio.play().catch(() => {});
    } else {
      audio.pause();
    }
  }, [snapshot, audioRate]);

  const lastNotifiedStatusRef = useRef<PlaybackStatus | null>(null);
  useEffect(() => {
    if (!snapshot || snapshot.empty) {
      return;
    }
    const current = snapshot.status;
    const previous = lastNotifiedStatusRef.current;
    lastNotifiedStatusRef.current = current;
    if (previous === null || previous === current) {
      return;
    }
    if (current === "CLEARED") {
      toast.warning(t("toast.cleared"));
    } else if (current === "ENDED") {
      toast.info(t("toast.ended"));
    }
  }, [snapshot, t]);

  if (!snapshot || snapshot.empty || !song) {
    return (
      <div className="pointer-events-auto flex items-center gap-2 rounded-full bg-neutral-900/80 px-4 py-2 text-xs text-neutral-300 ring-1 ring-white/10 backdrop-blur-md">
        <span>{t("empty")}</span>
        {isHost && onChooseSong ? (
          <Button
            type="button"
            size="sm"
            variant="secondary"
            onClick={onChooseSong}
          >
            {t("choose")}
          </Button>
        ) : null}
      </div>
    );
  }

  const durationSeconds = song.durationSeconds ?? 0;
  const progress =
    durationSeconds > 0
      ? Math.min(100, Math.max(0, (positionSeconds / durationSeconds) * 100))
      : 0;

  return (
    <div className="pointer-events-auto flex w-full max-w-xl flex-col gap-2 rounded-2xl bg-neutral-900/80 px-4 py-3 text-white shadow-xl ring-1 ring-white/10 backdrop-blur-md">
      <div className="flex items-center justify-between gap-3">
        <div className="flex min-w-0 flex-1 flex-col">
          <span className="truncate text-sm font-medium">{song.title}</span>
          {song.artist ? (
            <span className="truncate text-xs text-neutral-400">
              {song.artist}
            </span>
          ) : null}
        </div>
        <span className="text-[10px] uppercase tracking-wide text-neutral-400">
          {status ? statusLabel(t, status) : ""}
        </span>
      </div>

      <div className="flex items-center gap-3">
        <Button
          type="button"
          size="icon"
          variant="ghost"
          className="size-8 rounded-full text-neutral-300"
          onClick={() => seek(-1)}
          aria-label={t("seekBack")}
          title={t("seekBack")}
          disabled={!streamEnabled}
        >
          <SkipBack className="size-4" />
        </Button>

        <Button
          type="button"
          size="icon"
          variant={isPlaying ? "default" : "secondary"}
          className="size-10 rounded-full"
          onClick={() => (isPlaying ? pause() : play())}
          aria-label={isPlaying ? t("pause") : t("play")}
          title={isPlaying ? t("pause") : t("play")}
          disabled={!streamEnabled}
        >
          {isPlaying ? (
            <Pause className="size-4" />
          ) : (
            <Play className="size-4" />
          )}
        </Button>

        <Button
          type="button"
          size="icon"
          variant="ghost"
          className="size-8 rounded-full text-neutral-300"
          onClick={() => seek(1)}
          aria-label={t("seekForward")}
          title={t("seekForward")}
          disabled={!streamEnabled}
        >
          <SkipForward className="size-4" />
        </Button>

        <div className="flex flex-1 items-center gap-2 text-xs">
          <span className="tabular-nums">{formatTime(positionSeconds)}</span>
          <div className="relative h-1 flex-1 overflow-hidden rounded-full bg-white/10">
            <span
              className="absolute inset-y-0 left-0 bg-blue-400"
              style={{ width: `${progress}%` }}
            />
          </div>
          <span className="tabular-nums text-neutral-400">
            {formatTime(durationSeconds)}
          </span>
        </div>

        <select
          aria-label={t("rate")}
          value={audioRate}
          onChange={(e) => setRate(e.target.value)}
          className="rounded-md border border-white/10 bg-white/5 px-2 py-1 text-xs text-white"
          disabled={!streamEnabled}
        >
          {RATE_OPTIONS.map((option) => (
            <option key={option} value={option} className="bg-neutral-900">
              {option}x
            </option>
          ))}
        </select>

        <Button
          type="button"
          size="icon"
          variant={loopMode === "ONE" ? "default" : "ghost"}
          className={
            loopMode === "ONE"
              ? "size-8 rounded-full"
              : "size-8 rounded-full text-neutral-300"
          }
          onClick={() => setLoop(loopMode === "ONE" ? "OFF" : "ONE")}
          aria-label={t("loopOne")}
          title={t("loopOne")}
          disabled={!streamEnabled}
        >
          <Repeat className="size-4" />
        </Button>

        <Button
          type="button"
          size="icon"
          variant={shuffleEnabled ? "default" : "ghost"}
          className={
            shuffleEnabled
              ? "size-8 rounded-full"
              : "size-8 rounded-full text-neutral-300"
          }
          onClick={() => setShuffle(!shuffleEnabled)}
          aria-label={t("shuffle")}
          title={t("shuffle")}
          disabled={!streamEnabled}
        >
          <Shuffle className="size-4" />
        </Button>

        {isHost && onChooseSong ? (
          <Button
            type="button"
            size="icon"
            variant="ghost"
            className="size-8 rounded-full text-neutral-300"
            onClick={onChooseSong}
            aria-label={t("change")}
            title={t("change")}
          >
            <RefreshCw className="size-4" />
          </Button>
        ) : null}

        {needsReconcile ? (
          <Button
            type="button"
            size="icon"
            variant="ghost"
            className="size-8 rounded-full text-amber-400"
            onClick={reconcile}
            aria-label={t("reconcile")}
            title={t("reconcile")}
          >
            <X className="size-4" />
          </Button>
        ) : null}
      </div>

      <audio
        ref={audioRef}
        preload="metadata"
        playsInline
        className="hidden"
        aria-hidden
      />
    </div>
  );
}
