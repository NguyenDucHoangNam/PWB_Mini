"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Music2, Pause, Play, Volume2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { getRoomAudioUrl } from "../../api/music";
import { appDestinations } from "../../lib/liveroom-destinations";
import { liveroomSocket } from "../../lib/liveroom-socket";
import { LiveroomErrorCode } from "../../lib/liveroom-error-codes";
import { serverNow } from "../../lib/server-clock";
import { positionAt, usePlaybackPosition } from "../../hooks/use-playback-position";
import { useLiveroomStore } from "../../stores/use-liveroom-store";
import { RangeSlider } from "../ui/range-slider";
import { SongPickerDialog } from "./song-picker-dialog";

const DRIFT_TOLERANCE_S = 1.5;
const GET_STATE_DEBOUNCE_MS = 300;

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
  const position = usePlaybackPosition();

  const audioRef = useRef<HTMLAudioElement | null>(null);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [scrubbing, setScrubbing] = useState<number | null>(null);

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
    let cancelled = false;
    void (async () => {
      try {
        const response = await getRoomAudioUrl({ roomId });
        if (cancelled || !response.data?.url) return;
        audio.src = response.data.url;
        audio.load();
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
  const shown = scrubbing ?? position;

  return (
    <section className="flex shrink-0 flex-col gap-2 border-t border-neutral-200 p-3 dark:border-neutral-800">
      <div className="flex items-center gap-2">
        <Music2 className="size-4 shrink-0 text-neutral-400" aria-hidden />
        <div className="min-w-0 flex-1">
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
        <Button
          variant="outline"
          size="sm"
          className="h-11 shrink-0 md:h-8"
          onClick={() => setPickerOpen(true)}
        >
          {t("pickSong")}
        </Button>
      </div>

      <div className="flex items-center gap-2">
        <Button
          size="icon"
          className="size-11 shrink-0 md:size-9"
          aria-label={music?.status === "PLAYING" ? t("pause") : t("play")}
          title={playBlocked ? t("ownerAbsentHint") : undefined}
          disabled={!music?.songId || (music?.status !== "PLAYING" && playBlocked)}
          onClick={() => {
            if (music?.status === "PLAYING") publish(appDestinations.musicPause(roomId));
            else publish(appDestinations.musicPlay(roomId), {});
          }}
        >
          {music?.status === "PLAYING" ? (
            <Pause className="size-4" />
          ) : (
            <Play className="size-4" />
          )}
        </Button>

        <span className="shrink-0 text-xs tabular-nums text-neutral-500 dark:text-neutral-400">
          {formatClock(shown)}
        </span>
        <RangeSlider
          value={shown}
          max={duration}
          ariaLabel={t("seek")}
          disabled={!music?.songId}
          onChange={setScrubbing}
          onCommit={(value) => {
            setScrubbing(null);
            publish(appDestinations.musicSeek(roomId), { positionSeconds: value });
          }}
        />
        <span className="shrink-0 text-xs tabular-nums text-neutral-500 dark:text-neutral-400">
          {formatClock(duration)}
        </span>
      </div>

      <div className="flex items-center gap-2">
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