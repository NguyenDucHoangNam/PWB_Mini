"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import {
  ListMusic,
  Music2,
  Pause,
  Play,
  RotateCcw,
  RotateCw,
  Volume1,
  Volume2,
  VolumeX,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { getRoomAudioUrl } from "../../api/music";
import { appDestinations } from "../../lib/liveroom-destinations";
import { liveroomSocket } from "../../lib/liveroom-socket";
import { LiveroomErrorCode } from "../../lib/liveroom-error-codes";
import { serverNow } from "../../lib/server-clock";
import { positionAt, usePlaybackPosition } from "../../hooks/use-playback-position";
import { useLiveroomStore } from "../../stores/use-liveroom-store";
import { useWaveformPeaks } from "../../hooks/use-waveform-peaks";
import { RangeSlider } from "../ui/range-slider";
import { SongPickerDialog } from "./song-picker-dialog";
import { EMPTY_DRAFT, TrackCommentLane, type Draft } from "./track-comment-lane";
import { TrackWaveform } from "./track-waveform";

const DRIFT_TOLERANCE_S = 1.5;
const GET_STATE_DEBOUNCE_MS = 300;
const END_OF_TRACK_GRACE_MS = 250;
const SKIP_SECONDS = 10;
const DEFAULT_VOLUME_PERCENT = 100;
const HAVE_METADATA = 1;

function formatClock(totalSeconds: number): string {
  const safe = Math.max(0, Math.floor(totalSeconds));
  const minutes = Math.floor(safe / 60);
  const seconds = safe % 60;
  return `${minutes}:${String(seconds).padStart(2, "0")}`;
}

export function MusicPlayer({ roomId }: { roomId: string }) {
  const t = useTranslations("liveroom.room.music");
  const music = useLiveroomStore((state) => state.music.state);
  const reloadToken = useLiveroomStore((state) => state.music.reloadToken);
  const isOwner = useLiveroomStore((state) => state.isOwner);
  const comments = useLiveroomStore((state) => state.trackComments.items);
  const position = usePlaybackPosition();

  const audioRef = useRef<HTMLAudioElement | null>(null);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [audioUrl, setAudioUrl] = useState<{ songId: string; url: string } | null>(null);
  const [draft, setDraft] = useState<Draft>(EMPTY_DRAFT);
  const [volumeDraft, setVolumeDraft] = useState<{ value: number; base: number } | null>(null);
  const [audioBlocked, setAudioBlocked] = useState(false);
  const lastAudibleVolume = useRef(DEFAULT_VOLUME_PERCENT);

  const publish = useCallback(
    (destination: string, body?: unknown) => liveroomSocket.publish(destination, body),
    [],
  );

  useEffect(() => {
    if (!audioRef.current) audioRef.current = new Audio();
    const audio = audioRef.current;
    audio.preload = "auto";
    return () => {
      audio.pause();
      audio.src = "";
    };
  }, []);

  useEffect(() => {
    const audio = audioRef.current;
    if (!audio || !music?.songId) return;
    liveroomSocket.publish(appDestinations.commentsGet(roomId));
    let cancelled = false;
    void (async () => {
      try {
        const response = await getRoomAudioUrl({ roomId });
        if (cancelled || !response.data?.url) return;
        audio.src = response.data.url;
        audio.load();
        setAudioUrl({ songId: response.data.songId, url: response.data.url });
      } catch {
        if (!cancelled) toast.error(t("loadFailed"));
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [roomId, reloadToken, music?.songId, t]);

  const serverVolume = music?.volumePercent ?? DEFAULT_VOLUME_PERCENT;
  const volume = volumeDraft?.base === serverVolume ? volumeDraft.value : serverVolume;

  useEffect(() => {
    const audio = audioRef.current;
    if (!audio) return;
    audio.volume = Math.min(1, Math.max(0, volume / 100));
  }, [volume]);

  useEffect(() => {
    if (volume > 0) lastAudibleVolume.current = volume;
  }, [volume]);

  useEffect(() => {
    const audio = audioRef.current;
    if (!audio || !music?.songId || audioUrl?.songId !== music.songId) return;

    const sync = () => {
      const target = positionAt(music, serverNow());
      if (Math.abs(audio.currentTime - target) > DRIFT_TOLERANCE_S) {
        audio.currentTime = target;
      }
      if (music.status !== "PLAYING") {
        audio.pause();
        return;
      }
      void audio.play().then(
        () => setAudioBlocked(false),
        (error: unknown) => setAudioBlocked((error as DOMException)?.name === "NotAllowedError"),
      );
    };

    if (audio.readyState >= HAVE_METADATA) {
      sync();
      return;
    }
    audio.addEventListener("loadedmetadata", sync, { once: true });
    return () => audio.removeEventListener("loadedmetadata", sync);
  }, [music, reloadToken, audioUrl]);

  useEffect(() => {
    if (!isOwner || !music?.songId || music.status !== "PLAYING") return;
    const total = music.songDurationSeconds;
    if (!total) return;

    const pause = () => publish(appDestinations.musicPause(roomId));
    const remainingMs = (total - positionAt(music, serverNow())) * 1000;
    if (remainingMs <= 0) {
      pause();
      return;
    }
    const timer = window.setTimeout(pause, remainingMs + END_OF_TRACK_GRACE_MS);
    return () => window.clearTimeout(timer);
  }, [music, isOwner, roomId, publish]);

  useEffect(() => {
    const off = liveroomSocket.onFrameError((error) => {
      if (error.code !== LiveroomErrorCode.MUSIC_STATE_CONFLICT) return;
      window.setTimeout(
        () => publish(appDestinations.musicGetState(roomId)),
        GET_STATE_DEBOUNCE_MS,
      );
    });
    return off;
  }, [roomId, publish]);

  const revivedAt = useLiveroomStore((state) => state.lifecycle.revivedAt);
  useEffect(() => {
    if (revivedAt) publish(appDestinations.musicGetState(roomId));
  }, [revivedAt, roomId, publish]);

  const ownerAbsent = music?.ownerAbsent ?? false;
  const playBlocked = ownerAbsent && !isOwner;
  const duration = music?.songDurationSeconds ?? 0;
  const songId = music?.songId ?? null;
  const waveform = useWaveformPeaks(songId, audioUrl?.songId === songId ? audioUrl.url : null);
  const playing = music?.status === "PLAYING";
  const hasSong = Boolean(songId);

  const seekTo = (value: number) => {
    const target = Math.min(duration || value, Math.max(0, value));
    publish(appDestinations.musicSeek(roomId), { positionSeconds: target });
    if (draft.songId === songId && draft.pinned !== null) {
      setDraft({ ...draft, pinned: target });
    }
  };

  const skipBy = (delta: number) => {
    if (!hasSong) return;
    seekTo(position + delta);
  };

  const previewVolume = (value: number) => {
    setVolumeDraft({ value, base: serverVolume });
  };

  const commitVolume = (value: number) => {
    publish(appDestinations.musicVolume(roomId), { volumePercent: Math.round(value) });
  };

  const resumeAudio = () => {
    const audio = audioRef.current;
    if (!audio) return;
    void audio.play().then(
      () => setAudioBlocked(false),
      () => undefined,
    );
  };

  const toggleMute = () => {
    if (!hasSong) return;
    const next = volume > 0 ? 0 : lastAudibleVolume.current || DEFAULT_VOLUME_PERCENT;
    previewVolume(next);
    commitVolume(next);
  };

  const VolumeIcon = volume === 0 ? VolumeX : volume < 50 ? Volume1 : Volume2;

  return (
    <section className="flex shrink-0 flex-col gap-2 border-t border-neutral-200 bg-gradient-to-b from-neutral-50 to-white px-3 py-2.5 dark:border-neutral-800 dark:from-neutral-950 dark:to-black">
      <div className="flex items-center gap-2 md:gap-4">
        <div className="flex min-w-0 flex-1 items-center gap-2.5">
          <span
            aria-hidden
            className={`grid size-10 shrink-0 place-items-center rounded-xl bg-gradient-to-br from-neutral-700 to-neutral-900 text-white shadow-sm dark:from-neutral-100 dark:to-neutral-400 dark:text-black ${
              playing ? "animate-pulse" : ""
            }`}
          >
            <Music2 className="size-5" />
          </span>
          <div className="min-w-0">
            {music?.songTitle ? (
              <>
                <p className="truncate text-sm font-semibold text-black dark:text-white">
                  {music.songTitle}
                </p>
                <p className="truncate text-xs text-neutral-500 dark:text-neutral-400">
                  {music.songArtist ? t("by", { artist: music.songArtist }) : t("nowPlaying")}
                </p>
              </>
            ) : (
              <p className="truncate text-sm text-neutral-500 dark:text-neutral-400">
                {t("title")}
              </p>
            )}
          </div>
        </div>

        <div className="flex shrink-0 items-center gap-1">
          <Tooltip>
            <TooltipTrigger
              render={
                <Button
                  variant="ghost"
                  size="icon"
                  className="size-10 shrink-0 rounded-full"
                  aria-label={t("back10", { seconds: SKIP_SECONDS })}
                  disabled={!hasSong}
                  onClick={() => skipBy(-SKIP_SECONDS)}
                />
              }
            >
              <RotateCcw className="size-4" />
            </TooltipTrigger>
            <TooltipContent>{t("back10", { seconds: SKIP_SECONDS })}</TooltipContent>
          </Tooltip>

          <Tooltip>
            <TooltipTrigger
              render={
                <Button
                  size="icon"
                  className="size-12 shrink-0 rounded-full shadow-md md:size-11"
                  aria-label={playing ? t("pause") : t("play")}
                  disabled={!hasSong || (!playing && playBlocked)}
                  onClick={() => {
                    if (playing) publish(appDestinations.musicPause(roomId));
                    else publish(appDestinations.musicPlay(roomId), {});
                  }}
                />
              }
            >
              {playing ? (
                <Pause className="size-5 fill-current" />
              ) : (
                <Play className="size-5 fill-current" />
              )}
            </TooltipTrigger>
            <TooltipContent>
              {playBlocked ? t("ownerAbsentHint") : playing ? t("pause") : t("play")}
            </TooltipContent>
          </Tooltip>

          <Tooltip>
            <TooltipTrigger
              render={
                <Button
                  variant="ghost"
                  size="icon"
                  className="size-10 shrink-0 rounded-full"
                  aria-label={t("forward10", { seconds: SKIP_SECONDS })}
                  disabled={!hasSong}
                  onClick={() => skipBy(SKIP_SECONDS)}
                />
              }
            >
              <RotateCw className="size-4" />
            </TooltipTrigger>
            <TooltipContent>{t("forward10", { seconds: SKIP_SECONDS })}</TooltipContent>
          </Tooltip>
        </div>

        <div className="flex flex-1 items-center justify-end gap-2 md:gap-3">
          <span className="shrink-0 text-xs font-medium tabular-nums text-neutral-500 dark:text-neutral-400">
            {formatClock(position)}
            <span className="mx-0.5 text-neutral-300 dark:text-neutral-600">/</span>
            {formatClock(duration)}
          </span>

          <div className="flex shrink-0 items-center gap-1.5">
            <Tooltip>
              <TooltipTrigger
                render={
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    className="shrink-0 rounded-full text-neutral-500 dark:text-neutral-400"
                    aria-label={volume === 0 ? t("unmute") : t("mute")}
                    disabled={!hasSong}
                    onClick={toggleMute}
                  />
                }
              >
                <VolumeIcon className="size-4" />
              </TooltipTrigger>
              <TooltipContent>{volume === 0 ? t("unmute") : t("mute")}</TooltipContent>
            </Tooltip>
            <RangeSlider
              className="w-16 md:w-24"
              value={volume}
              max={100}
              ariaLabel={t("volume")}
              disabled={!hasSong}
              onChange={previewVolume}
              onCommit={commitVolume}
            />
          </div>

          <Tooltip>
            <TooltipTrigger
              render={
                <Button
                  variant="outline"
                  size="lg"
                  className="shrink-0 rounded-full"
                  onClick={() => setPickerOpen(true)}
                />
              }
            >
              <ListMusic className="size-4" />
              <span className="hidden md:inline">{t("pickSong")}</span>
            </TooltipTrigger>
            <TooltipContent>{t("pickSong")}</TooltipContent>
          </Tooltip>
        </div>
      </div>

      {audioBlocked && playing ? (
        <button
          type="button"
          onClick={resumeAudio}
          className="flex items-center justify-center gap-2 rounded-lg border border-amber-300 bg-amber-50 px-3 py-1.5 text-xs font-medium text-amber-900 hover:bg-amber-100 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200 dark:hover:bg-amber-950/70"
        >
          <VolumeX className="size-4 shrink-0" aria-hidden />
          {t("blockedHint")}
        </button>
      ) : null}

      <TrackWaveform
        waveform={waveform}
        position={position}
        durationSeconds={duration}
        comments={comments}
        pinnedPosition={draft.songId === songId ? draft.pinned : null}
        onSeek={seekTo}
      />

      <TrackCommentLane
        roomId={roomId}
        songId={songId}
        position={position}
        draft={draft}
        onDraftChange={setDraft}
      />

      {playBlocked ? (
        <p className="text-xs text-amber-700 dark:text-amber-400">{t("ownerAbsentHint")}</p>
      ) : null}

      <SongPickerDialog
        open={pickerOpen}
        onOpenChange={setPickerOpen}
        onPick={(songId) => publish(appDestinations.musicPlay(roomId), { songId })}
      />
    </section>
  );
}