"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslations } from "next-intl";
import { Play, Pause, Volume2, VolumeX } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { usePresignedUrl } from "../hooks/use-presigned-url";
import { getSongAudioUrl, songStreamKey } from "../api/song-stream";
import { formatDuration } from "../lib/format-audio";

interface AudioPlayerProps {
  songId: string;
}

const WAVEFORM_BARS = 100;
const MOBILE_WAVEFORM_BARS = 48;
const MOBILE_BREAKPOINT_PX = 640;
const SEEK_STEP_PERCENT = 5;
const WAVEFORM_HEIGHT_PX = 64;
const WAVEFORM_MIN_BAR_PX = 3;

// Deterministic stand-in for random skeleton bar heights. Keeping this pure means the
// placeholder holds still across re-renders instead of reshuffling on every paint.
function skeletonBarHeight(index: number): number {
  const noise = Math.abs((Math.sin((index + 1) * 12.9898) * 43758.5453) % 1);
  return Math.max(WAVEFORM_MIN_BAR_PX, Math.round((0.2 + noise * 0.5) * WAVEFORM_HEIGHT_PX));
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

function resampleBars(bars: number[], targetCount: number): number[] {
  if (bars.length === 0 || targetCount >= bars.length) return bars;

  const groupSize = bars.length / targetCount;
  return Array.from({ length: targetCount }, (_, i) => {
    const start = Math.floor(i * groupSize);
    const end = Math.min(Math.floor((i + 1) * groupSize), bars.length);
    let sum = 0;
    for (let j = start; j < end; j++) sum += bars[j];
    return sum / Math.max(end - start, 1);
  });
}

/** Fewer, wider bars on a phone: 100 bars across a 320px column renders as unreadable hairlines. */
function useWaveformBarCount(): number {
  const [barCount, setBarCount] = useState(WAVEFORM_BARS);

  useEffect(() => {
    const mediaQuery = window.matchMedia(`(max-width: ${MOBILE_BREAKPOINT_PX - 1}px)`);
    const sync = () => setBarCount(mediaQuery.matches ? MOBILE_WAVEFORM_BARS : WAVEFORM_BARS);

    sync();
    mediaQuery.addEventListener("change", sync);
    return () => mediaQuery.removeEventListener("change", sync);
  }, []);

  return barCount;
}

function WaveformBars({
  bars,
  progressPercent,
  onSeek,
  label,
}: {
  bars: number[];
  progressPercent: number;
  onSeek: (percent: number) => void;
  label: string;
}) {
  const containerRef = useRef<HTMLDivElement>(null);

  const handleClick = (e: React.MouseEvent<HTMLDivElement>) => {
    if (!containerRef.current) return;
    const rect = containerRef.current.getBoundingClientRect();
    onSeek(((e.clientX - rect.left) / rect.width) * 100);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "ArrowRight") {
      e.preventDefault();
      onSeek(progressPercent + SEEK_STEP_PERCENT);
    } else if (e.key === "ArrowLeft") {
      e.preventDefault();
      onSeek(progressPercent - SEEK_STEP_PERCENT);
    }
  };

  const playedBars = Math.floor((progressPercent / 100) * bars.length);

  return (
    <div
      ref={containerRef}
      className="flex h-12 cursor-pointer items-end gap-px rounded-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring sm:h-16 sm:gap-0.5"
      onClick={handleClick}
      onKeyDown={handleKeyDown}
      role="slider"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={Math.round(progressPercent)}
      tabIndex={0}
    >
      {bars.map((height, i) => (
        <div
          key={i}
          className={`flex-1 rounded-full beat-16th transition-colors ease-hammer ${
            i < playedBars ? "bg-primary" : "bg-border"
          }`}
          style={{
            height: `${Math.max(WAVEFORM_MIN_BAR_PX, Math.round(height * WAVEFORM_HEIGHT_PX))}px`,
          }}
        />
      ))}
    </div>
  );
}

function WaveformSkeleton({ barCount }: { barCount: number }) {
  return (
    <div className="flex h-12 items-end gap-px sm:h-16 sm:gap-0.5" aria-hidden="true">
      {Array.from({ length: barCount }).map((_, i) => (
        <div
          key={i}
          className="flex-1 animate-pulse rounded-full bg-muted"
          style={{ height: `${skeletonBarHeight(i)}px`, animationDelay: `${i * 15}ms` }}
        />
      ))}
    </div>
  );
}

function SongCustomPlayer({ url }: { url: string }) {
  const tActions = useTranslations("voice.actions");
  const tPlayer = useTranslations("voice.player");
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [isMuted, setIsMuted] = useState(false);
  const [waveform, setWaveform] = useState<number[]>([]);
  const [waveformLoading, setWaveformLoading] = useState(true);
  const barCount = useWaveformBarCount();

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
          const fallback = Array.from({ length: WAVEFORM_BARS }, () => 0.2 + Math.random() * 0.8);
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

  const visibleBars = useMemo(() => resampleBars(waveform, barCount), [waveform, barCount]);

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
      const newTime = (Math.min(Math.max(percent, 0), 100) / 100) * duration;
      audioRef.current.currentTime = newTime;
      setCurrentTime(newTime);
    },
    [duration],
  );

  const progressPercent = duration > 0 ? (currentTime / duration) * 100 : 0;

  return (
    <div className="flex flex-col gap-4">
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
        <WaveformSkeleton barCount={barCount} />
      ) : (
        <WaveformBars
          bars={visibleBars}
          progressPercent={progressPercent}
          onSeek={handleSeek}
          label={tPlayer("seek")}
        />
      )}

      <div className="flex items-center gap-3 sm:gap-4">
        <Button
          size="icon"
          className="size-11 shrink-0 rounded-full sm:size-10"
          onClick={togglePlay}
          aria-label={isPlaying ? tPlayer("pause") : tActions("play")}
        >
          {isPlaying ? (
            <Pause className="size-4 fill-current" aria-hidden="true" />
          ) : (
            <Play className="ml-0.5 size-4 fill-current" aria-hidden="true" />
          )}
        </Button>

        <span className="shrink-0 text-xs tabular-nums text-foreground">
          {formatDuration(currentTime)}
        </span>

        <div
          className="group/track relative h-6 flex-1 cursor-pointer"
          onClick={(e) => {
            const rect = e.currentTarget.getBoundingClientRect();
            handleSeek(((e.clientX - rect.left) / rect.width) * 100);
          }}
        >
          <div className="absolute inset-x-0 top-1/2 h-1 -translate-y-1/2 overflow-hidden rounded-full bg-border">
            <div className="h-full rounded-full bg-primary" style={{ width: `${progressPercent}%` }} />
          </div>
          <div
            className="absolute top-1/2 size-3 -translate-y-1/2 rounded-full bg-primary opacity-0 shadow-sm beat-16th transition-opacity ease-hammer group-hover/track:opacity-100"
            style={{ left: `calc(${progressPercent}% - 0.375rem)` }}
            aria-hidden="true"
          />
        </div>

        <span className="shrink-0 text-xs tabular-nums text-muted-foreground">
          {formatDuration(duration)}
        </span>

        <Button
          variant="ghost"
          size="icon"
          className="size-9 shrink-0 text-muted-foreground hover:text-foreground sm:size-8"
          onClick={toggleMute}
          aria-label={isMuted ? tPlayer("unmute") : tPlayer("mute")}
        >
          {isMuted ? (
            <VolumeX className="size-4" aria-hidden="true" />
          ) : (
            <Volume2 className="size-4" aria-hidden="true" />
          )}
        </Button>
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
      <div role="status" className="flex items-center justify-center gap-2 py-6 text-sm text-muted-foreground">
        <Spinner size="sm" />
        {t("songLabel")}
      </div>
    );
  }

  if (query.isError || !query.data?.data?.url) {
    return (
      <p role="alert" className="py-4 text-center text-sm text-destructive">
        {t("loadError")}
      </p>
    );
  }

  return <SongCustomPlayer url={query.data.data.url} />;
}
