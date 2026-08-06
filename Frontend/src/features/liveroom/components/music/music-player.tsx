"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { ListMusic, Music2, Pause, Play, Volume2 } from "lucide-react";
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

  useEffect(() => {
    const audio = audioRef.current;
    if (!audio || !music) return;
    audio.volume = Math.min(1, Math.max(0, music.volumePercent / 100));
  }, [music?.volumePercent, music]);

  useEffect(() => {
    const audio = audioRef.current;
    if (!audio || !music?.songId) return;
    const target = positionAt(music, serverNow());
    if (Math.abs(audio.currentTime - target) > DRIFT_TOLERANCE_S) {
      audio.currentTime = target;
    }
    if (music.status === "PLAYING") {
      void audio.play().catch(() => undefined);
    } else {
      audio.pause();
    }
  }, [music, reloadToken]);

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



  const seekTo = (value: number) => {
    publish(appDestinations.musicSeek(roomId), { positionSeconds: value });
    if (draft.songId === songId && draft.pinned !== null) {
      setDraft({ ...draft, pinned: value });
    }
  };

  return (
    <section className="flex shrink-0 flex-col gap-2 border-t border-neutral-200 bg-neutral-50 px-3 py-2 dark:border-neutral-800 dark:bg-neutral-950">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
        <Tooltip>
          <TooltipTrigger
            render={
              <Button
                size="icon"
                className="size-11 shrink-0 md:size-10"
                aria-label={music?.status === "PLAYING" ? t("pause") : t("play")}
                disabled={!music?.songId || (music?.status !== "PLAYING" && playBlocked)}
                onClick={() => {
                  if (music?.status === "PLAYING") publish(appDestinations.musicPause(roomId));
                  else publish(appDestinations.musicPlay(roomId), {});
                }}
              />
            }
          >
            {music?.status === "PLAYING" ? (
              <Pause className="size-5 md:size-4" />
            ) : (
              <Play className="size-5 md:size-4" />
            )}
          </TooltipTrigger>
          <TooltipContent>
            {playBlocked
              ? t("ownerAbsentHint")
              : music?.status === "PLAYING"
                ? t("pause")
                : t("play")}
          </TooltipContent>
        </Tooltip>

        <div className="flex min-w-0 basis-40 items-center gap-2 md:basis-56">
          <Music2 className="size-4 shrink-0 text-neutral-400" aria-hidden />
          <div className="min-w-0">
            {music?.songTitle ? (
              <>
                <p className="truncate text-sm font-medium text-black dark:text-white">
                  {music.songTitle}
                </p>
                {music.songArtist ? (
                  <p className="truncate text-xs text-neutral-500 dark:text-neutral-400">
                    {t("by", { artist: music.songArtist })}
                  </p>
                ) : null}
              </>
            ) : (
              <p className="truncate text-sm text-neutral-500 dark:text-neutral-400">
                {t("title")}
              </p>
            )}
          </div>
        </div>

        <span className="ml-auto shrink-0 text-xs tabular-nums text-neutral-500 dark:text-neutral-400">
          {formatClock(position)} / {formatClock(duration)}
        </span>

        <div className="flex w-32 shrink-0 items-center gap-2">
          <Volume2 className="size-4 shrink-0 text-neutral-400" aria-hidden />
          <RangeSlider
            value={music?.volumePercent ?? 100}
            max={100}
            ariaLabel={t("volume")}
            disabled={!music?.songId}
            onCommit={(value) =>
              publish(appDestinations.musicVolume(roomId), { volumePercent: Math.round(value) })
            }
          />
        </div>

        <Tooltip>
          <TooltipTrigger
            render={
              <Button
                variant="outline"
                className="h-11 shrink-0 md:h-10"
                onClick={() => setPickerOpen(true)}
              />
            }
          >
            <ListMusic className="size-4" />
            <span className="hidden sm:inline">{t("pickSong")}</span>
          </TooltipTrigger>
          <TooltipContent>{t("pickSong")}</TooltipContent>
        </Tooltip>
      </div>

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