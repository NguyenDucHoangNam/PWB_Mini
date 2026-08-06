"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslations } from "next-intl";
import { Play, Pause, Volume2, VolumeX } from "lucide-react";
import { Spinner } from "@/components/ui/spinner";
import { usePresignedUrl } from "../hooks/use-presigned-url";
import { getSongAudioUrl, songStreamKey } from "../api/song-stream";

interface AudioPlayerProps {
  songId: string;
}

const WAVEFORM_BARS = 100;

// Deterministic stand-in for random skeleton bar heights. Keeping this pure means the
// placeholder holds still across re-renders instead of reshuffling on every paint.
function skeletonBarHeight(index: number): number {
  const noise = Math.abs((Math.sin((index + 1) * 12.9898) * 43758.5453) % 1);
  return Math.max(3, Math.round((0.2 + noise * 0.5) * 64));
}

function formatTime(timeInSec: number) {
  if (!Number.isFinite(timeInSec)) return "0:00";
  const mins = Math.floor(timeInSec / 60);
  const secs = Math.floor(timeInSec % 60);
  return `${mins}:${secs < 10 ? "0" : ""}${secs}`;
}

function extractWaveformData(audioBuffer: AudioBuffer, barCount: number): number[] {
  const rawData = audioBuffer.getChannelData(0);
  const samplesPerBar = Math.floor(rawData.length / barCount);
  const bars: number[] = [];

  for (let i = 0; i < barCount; i++) {
    let sum = 0;
    const start = i * samplesPerBar;
    for (let j = start; j < start + samplesPerBar && j < rawData.length; j++) {
      sum += Math.abs(rawData[j]);
    }
    bars.push(sum / samplesPerBar);
  }

  const maxVal = Math.max(...bars, 0.01);
  return bars.map((v) => v / maxVal);
}

function WaveformBars({
  bars,
  progressPercent,
  onSeek,
}: {
  bars: number[];
  progressPercent: number;
  onSeek: (percent: number) => void;
}) {
  const containerRef = useRef<HTMLDivElement>(null);

  const handleClick = (e: React.MouseEvent<HTMLDivElement>) => {
    if (!containerRef.current) return;
    const rect = containerRef.current.getBoundingClientRect();
    const x = e.clientX - rect.left;
    onSeek((x / rect.width) * 100);
  };

  const playedBars = Math.floor((progressPercent / 100) * bars.length);

  return (
    <div
      ref={containerRef}
      className="flex h-16 cursor-pointer items-end gap-[2px]"
      onClick={handleClick}
      role="slider"
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={Math.round(progressPercent)}
      tabIndex={0}
    >
      {bars.map((height, i) => {
        const isPlayed = i < playedBars;
        const minH = 3;
        const maxH = 64;
        const barH = Math.max(minH, Math.round(height * maxH));

        return (
          <div
            key={i}
            className={`flex-1 rounded-full transition-colors duration-150 ${
              isPlayed
                ? "bg-black dark:bg-white"
                : "bg-neutral-200 dark:bg-neutral-800"
            }`}
            style={{ height: `${barH}px` }}
          />
        );
      })}
    </div>
  );
}

function SongCustomPlayer({ url }: { url: string }) {
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [isMuted, setIsMuted] = useState(false);
  const [waveform, setWaveform] = useState<number[]>([]);
  const [waveformLoading, setWaveformLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;

    async function loadWaveform() {
      try {
        const audioContext = new AudioContext();
        const response = await fetch(url);
        const arrayBuffer = await response.arrayBuffer();
        const audioBuffer = await audioContext.decodeAudioData(arrayBuffer);
        if (!cancelled) {
          setWaveform(extractWaveformData(audioBuffer, WAVEFORM_BARS));
          setWaveformLoading(false);
        }
        await audioContext.close();
      } catch {
        if (!cancelled) {
          const fallback = Array.from({ length: WAVEFORM_BARS }, () =>
            0.2 + Math.random() * 0.8
          );
          setWaveform(fallback);
          setWaveformLoading(false);
        }
      }
    }

    loadWaveform();
    return () => {
      cancelled = true;
    };
  }, [url]);

  const togglePlay = () => {
    if (!audioRef.current) return;
    if (isPlaying) {
      audioRef.current.pause();
    } else {
      audioRef.current.play();
    }
  };

  const toggleMute = () => {
    if (!audioRef.current) return;
    audioRef.current.muted = !isMuted;
    setIsMuted(!isMuted);
  };

  const handleSeek = useCallback(
    (percent: number) => {
      if (!audioRef.current || !duration) return;
      const newTime = (percent / 100) * duration;
      audioRef.current.currentTime = newTime;
      setCurrentTime(newTime);
    },
    [duration],
  );

  const progressPercent = duration > 0 ? (currentTime / duration) * 100 : 0;

  return (
    <div className="flex flex-col gap-3">
      <audio
        ref={audioRef}
        src={url}
        preload="metadata"
        onPlay={() => setIsPlaying(true)}
        onPause={() => setIsPlaying(false)}
        onTimeUpdate={() => audioRef.current && setCurrentTime(audioRef.current.currentTime)}
        onLoadedMetadata={() => audioRef.current && setDuration(audioRef.current.duration)}
        onEnded={() => {
          setIsPlaying(false);
          setCurrentTime(0);
        }}
      />

      {waveformLoading ? (
        <div className="flex h-16 items-center justify-center">
          <div className="flex items-end gap-[2px] h-16">
            {Array.from({ length: WAVEFORM_BARS }).map((_, i) => (
              <div
                key={i}
                className="flex-1 min-w-[2px] rounded-full bg-neutral-100 dark:bg-neutral-900 animate-pulse"
                style={{
                  height: `${skeletonBarHeight(i)}px`,
                  animationDelay: `${i * 15}ms`,
                }}
              />
            ))}
          </div>
        </div>
      ) : (
        <WaveformBars
          bars={waveform}
          progressPercent={progressPercent}
          onSeek={handleSeek}
        />
      )}

      <div className="flex items-center gap-4">
        <button
          type="button"
          onClick={togglePlay}
          className="key-press flex size-10 shrink-0 items-center justify-center rounded-full bg-black text-white hover:scale-105 dark:bg-white dark:text-black"
          aria-label={isPlaying ? "Pause" : "Play"}
        >
          {isPlaying ? (
            <Pause className="size-4 fill-current" />
          ) : (
            <Play className="size-4 fill-current ml-0.5" />
          )}
        </button>

        <div className="flex flex-1 items-center gap-3">
          <span className="text-xs font-mono text-neutral-600 dark:text-neutral-400 shrink-0 min-w-[32px] text-right tabular-nums">
            {formatTime(currentTime)}
          </span>

          <div
            className="relative h-1 flex-1 overflow-hidden rounded-full bg-neutral-200 dark:bg-neutral-800 cursor-pointer group"
            onClick={(e) => {
              const rect = e.currentTarget.getBoundingClientRect();
              const x = e.clientX - rect.left;
              handleSeek((x / rect.width) * 100);
            }}
          >
            <div
              className="h-full rounded-full bg-black dark:bg-white transition-[width] duration-100"
              style={{ width: `${progressPercent}%` }}
            />
            <div
              className="absolute top-1/2 -translate-y-1/2 size-3 rounded-full bg-black dark:bg-white opacity-0 group-hover:opacity-100 transition-opacity shadow-sm"
              style={{ left: `calc(${progressPercent}% - 6px)` }}
            />
          </div>

          <span className="text-xs font-mono text-neutral-400 dark:text-neutral-500 shrink-0 min-w-[32px] tabular-nums">
            {formatTime(duration)}
          </span>
        </div>

        <button
          type="button"
          onClick={toggleMute}
          className="text-neutral-400 hover:text-black dark:hover:text-white transition-colors shrink-0"
          aria-label={isMuted ? "Unmute" : "Mute"}
        >
          {isMuted ? <VolumeX className="size-4" /> : <Volume2 className="size-4" />}
        </button>
      </div>
    </div>
  );
}

export function AudioPlayer({ songId }: AudioPlayerProps) {
  const t = useTranslations("voice.player");

  const query = usePresignedUrl({
    fetcher: () => getSongAudioUrl({ songId }),
    enabled: true,
    queryKey: songStreamKey(songId),
  });

  if (query.isLoading) {
    return (
      <div className="flex items-center justify-center gap-2 py-6 text-sm text-neutral-500">
        <Spinner size="sm" />
        {t("songLabel")}
      </div>
    );
  }

  if (query.isError || !query.data?.data?.url) {
    return (
      <div className="py-4 text-center text-sm text-red-600 dark:text-red-400">
        {t("loadError")}
      </div>
    );
  }

  return <SongCustomPlayer url={query.data.data.url} />;
}
