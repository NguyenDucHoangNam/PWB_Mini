"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslations } from "next-intl";
import { Play, Pause, Volume2, VolumeX, Repeat, Gauge, SkipBack, SkipForward } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { usePresignedUrl } from "../hooks/use-presigned-url";
import { getSongAudioUrl, songStreamKey } from "../api/song-stream";
import { formatDuration } from "../lib/format-audio";

interface AudioPlayerProps {
  songId: string;
}

const MOBILE_BREAKPOINT_PX = 640;
const SEEK_STEP_PERCENT = 5;
const WAVE_RESOLUTION = 800;
const DESKTOP_BARS = 360;
const MOBILE_BARS = 170;
// SoundCloud shape: tall crest on top, shorter dimmed reflection below the centre line.
const CREST_PX = 78;
const REFLECTION_PX = 40;
const MOBILE_CREST_PX = 50;
const MOBILE_REFLECTION_PX = 26;
const MIN_BAR_PX = 2;
const PLAYBACK_SPEEDS = [0.5, 0.75, 1, 1.25, 1.5, 2] as const;

function skeletonBarHeight(index: number, maxPx: number): number {
  const noise = Math.abs((Math.sin((index + 1) * 12.9898) * 43758.5453) % 1);
  return Math.max(MIN_BAR_PX, Math.round((0.25 + noise * 0.55) * maxPx));
}

function extractWaveformData(audioBuffer: AudioBuffer, barCount: number): number[] {
  const rawData = audioBuffer.getChannelData(0);
  const samplesPerBar = Math.max(1, Math.floor(rawData.length / barCount));
  const bars: number[] = [];

  for (let i = 0; i < barCount; i++) {
    let peak = 0;
    let sumSq = 0;
    let count = 0;
    const start = i * samplesPerBar;
    for (let j = start; j < start + samplesPerBar && j < rawData.length; j++) {
      const amplitude = Math.abs(rawData[j]);
      if (amplitude > peak) peak = amplitude;
      sumSq += rawData[j] * rawData[j];
      count++;
    }
    const rms = count > 0 ? Math.sqrt(sumSq / count) : 0;
    // Peak drives the silhouette, RMS gives the body — this reads as a real wave.
    bars.push(0.7 * peak + 0.3 * rms);
  }

  // Normalise to a high percentile, not the single loudest peak, so a lone spike
  // doesn't crush the whole track flat — quiet and loud sections stay distinct.
  const sorted = [...bars].sort((a, b) => a - b);
  const reference = sorted[Math.floor(sorted.length * 0.95)] || Math.max(...bars, 0.01);
  // Exponent > 1 deepens the valleys while peaks stay near full height — high contrast.
  return bars.map((value) => Math.min(1, Math.pow(value / reference, 1.6)));
}

function resampleBars(bars: number[], targetCount: number): number[] {
  if (bars.length === 0 || targetCount >= bars.length) return bars;

  const groupSize = bars.length / targetCount;
  return Array.from({ length: targetCount }, (_, i) => {
    const start = Math.floor(i * groupSize);
    const end = Math.min(Math.floor((i + 1) * groupSize), bars.length);
    let peak = 0;
    for (let j = start; j < end; j++) peak = Math.max(peak, bars[j]);
    return peak;
  });
}

interface WaveformDimensions {
  bars: number;
  crestPx: number;
  reflectionPx: number;
}

function useWaveformDimensions(): WaveformDimensions {
  const [dims, setDims] = useState<WaveformDimensions>({
    bars: DESKTOP_BARS,
    crestPx: CREST_PX,
    reflectionPx: REFLECTION_PX,
  });

  useEffect(() => {
    const mq = window.matchMedia(`(max-width: ${MOBILE_BREAKPOINT_PX - 1}px)`);
    const sync = () =>
      setDims(
        mq.matches
          ? { bars: MOBILE_BARS, crestPx: MOBILE_CREST_PX, reflectionPx: MOBILE_REFLECTION_PX }
          : { bars: DESKTOP_BARS, crestPx: CREST_PX, reflectionPx: REFLECTION_PX },
      );
    sync();
    mq.addEventListener("change", sync);
    return () => mq.removeEventListener("change", sync);
  }, []);

  return dims;
}

function barTone(index: number, playedBars: number, hoveredBars: number): string {
  if (index < playedBars) return "bg-foreground";
  if (index < hoveredBars) return "bg-muted-foreground/55";
  return "bg-muted-foreground/25";
}

function Waveform({
  bars,
  dims,
  progressPercent,
  onSeek,
  label,
}: {
  bars: number[];
  dims: WaveformDimensions;
  progressPercent: number;
  onSeek: (percent: number) => void;
  label: string;
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [hoverPercent, setHoverPercent] = useState<number | null>(null);

  const percentFromClientX = (clientX: number) => {
    const rect = containerRef.current?.getBoundingClientRect();
    if (!rect || rect.width === 0) return 0;
    return Math.min(Math.max(((clientX - rect.left) / rect.width) * 100, 0), 100);
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

  const playedBars = (progressPercent / 100) * bars.length;
  const hoveredBars = hoverPercent === null ? 0 : (hoverPercent / 100) * bars.length;

  const renderBars = (maxPx: number, alignClass: string, dimmed: boolean) => (
    <div className={`flex gap-px ${alignClass}`} style={{ height: `${maxPx}px` }}>
      {bars.map((height, i) => (
        <div
          key={i}
          className={`min-w-px flex-1 rounded-[1px] beat-16th transition-colors ease-hammer ${barTone(i, playedBars, hoveredBars)} ${dimmed ? "opacity-45" : ""}`}
          style={{ height: `${Math.max(MIN_BAR_PX, Math.round(height * maxPx))}px` }}
        />
      ))}
    </div>
  );

  return (
    <div
      ref={containerRef}
      className="group/wave relative w-full cursor-pointer select-none focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-ring"
      onClick={(e) => onSeek(percentFromClientX(e.clientX))}
      onMouseMove={(e) => setHoverPercent(percentFromClientX(e.clientX))}
      onMouseLeave={() => setHoverPercent(null)}
      onKeyDown={handleKeyDown}
      role="slider"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={Math.round(progressPercent)}
      tabIndex={0}
    >
      {renderBars(dims.crestPx, "items-end", false)}
      {renderBars(dims.reflectionPx, "items-start", true)}

      {/* Centre baseline where crest meets reflection. */}
      <div
        className="pointer-events-none absolute inset-x-0 h-px bg-muted-foreground/30"
        style={{ top: `${dims.crestPx}px` }}
        aria-hidden="true"
      />

      {/* Start marker at the left edge. */}
      <div className="pointer-events-none absolute left-0 top-0 bottom-0 w-px bg-foreground/40" aria-hidden="true" />

      {hoverPercent !== null && (
        <div
          className="pointer-events-none absolute inset-y-0 w-px bg-foreground/40"
          style={{ left: `${hoverPercent}%` }}
          aria-hidden="true"
        />
      )}
    </div>
  );
}

function WaveformSkeleton({ dims }: { dims: WaveformDimensions }) {
  const bars = Array.from({ length: dims.bars });

  const renderBars = (maxPx: number, alignClass: string, dimmed: boolean) => (
    <div className={`flex gap-px ${alignClass} ${dimmed ? "opacity-45" : ""}`} style={{ height: `${maxPx}px` }}>
      {bars.map((_, i) => (
        <div
          key={i}
          className="min-w-px flex-1 animate-pulse rounded-[1px] bg-muted"
          style={{
            height: `${skeletonBarHeight(i, maxPx)}px`,
            animationDelay: `${i * 6}ms`,
          }}
        />
      ))}
    </div>
  );

  return (
    <div className="w-full" aria-hidden="true">
      {renderBars(dims.crestPx, "items-end", false)}
      {renderBars(dims.reflectionPx, "items-start", true)}
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
  const [volume, setVolume] = useState(1);
  const [isLooping, setIsLooping] = useState(false);
  const [speedIndex, setSpeedIndex] = useState(2);
  const [waveform, setWaveform] = useState<number[]>([]);
  const [waveformLoading, setWaveformLoading] = useState(true);
  const dims = useWaveformDimensions();

  useEffect(() => {
    let cancelled = false;

    async function loadWaveform() {
      try {
        const audioContext = new AudioContext();
        const response = await fetch(url);
        const arrayBuffer = await response.arrayBuffer();
        const audioBuffer = await audioContext.decodeAudioData(arrayBuffer);
        if (!cancelled) {
          setWaveform(extractWaveformData(audioBuffer, WAVE_RESOLUTION));
          setWaveformLoading(false);
        }
        await audioContext.close();
      } catch {
        if (!cancelled) {
          const fallback = Array.from({ length: WAVE_RESOLUTION }, () => 0.2 + Math.random() * 0.8);
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

  const visibleBars = useMemo(() => resampleBars(waveform, dims.bars), [waveform, dims.bars]);

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

  const toggleLoop = () => {
    if (!audioRef.current) return;
    const next = !isLooping;
    audioRef.current.loop = next;
    setIsLooping(next);
  };

  const cycleSpeed = () => {
    if (!audioRef.current) return;
    const next = (speedIndex + 1) % PLAYBACK_SPEEDS.length;
    audioRef.current.playbackRate = PLAYBACK_SPEEDS[next];
    setSpeedIndex(next);
  };

  const handleVolumeChange = (value: number) => {
    if (!audioRef.current) return;
    audioRef.current.volume = value;
    audioRef.current.muted = value === 0;
    setVolume(value);
    setIsMuted(value === 0);
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
  const currentSpeed = PLAYBACK_SPEEDS[speedIndex];

  return (
    <div className="flex w-full flex-col gap-5">
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

      <div className="flex items-stretch gap-3 sm:gap-4">
        <div className="min-w-0 flex-1">
          {waveformLoading ? (
            <WaveformSkeleton dims={dims} />
          ) : (
            <Waveform
              bars={visibleBars}
              dims={dims}
              progressPercent={progressPercent}
              onSeek={handleSeek}
              label={tPlayer("seek")}
            />
          )}
        </div>

        {/* Stacked time readout, SoundCloud-style: elapsed on top, total below. */}
        <div className="flex w-10 shrink-0 flex-col justify-between py-0.5 text-right text-xs tabular-nums sm:w-12">
          <span className="font-medium text-foreground">{formatDuration(currentTime)}</span>
          <span className="text-muted-foreground">{formatDuration(duration)}</span>
        </div>
      </div>

      <div className="flex flex-col gap-2">
        <div className="grid grid-cols-3 items-center gap-2">
          {/* Left cluster: volume */}
          <div className="flex items-center justify-start gap-1">
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
            <input
              type="range"
              min={0}
              max={100}
              value={Math.round((isMuted ? 0 : volume) * 100)}
              onChange={(e) => handleVolumeChange(Number(e.target.value) / 100)}
              aria-label={tPlayer("volume")}
              className="hidden h-1 w-20 cursor-pointer appearance-none rounded-full bg-muted-foreground/20 accent-foreground outline-none focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-ring sm:block [&::-webkit-slider-thumb]:size-3 [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:bg-foreground [&::-moz-range-thumb]:size-3 [&::-moz-range-thumb]:appearance-none [&::-moz-range-thumb]:rounded-full [&::-moz-range-thumb]:border-0 [&::-moz-range-thumb]:bg-foreground"
            />
          </div>

          {/* Center cluster: transport */}
          <div className="flex items-center justify-center gap-2">
            <Button
              variant="ghost"
              size="icon"
              className="size-10 shrink-0 text-muted-foreground hover:text-foreground sm:size-9"
              onClick={() => handleSeek(progressPercent - SEEK_STEP_PERCENT * 2)}
              aria-label={tPlayer("seek")}
            >
              <SkipBack className="size-4" aria-hidden="true" />
            </Button>
            <Button
              size="icon"
              className="size-14 rounded-full shadow-sm sm:size-12"
              onClick={togglePlay}
              aria-label={isPlaying ? tPlayer("pause") : tActions("play")}
            >
              {isPlaying ? (
                <Pause className="size-5 fill-current" aria-hidden="true" />
              ) : (
                <Play className="ml-0.5 size-5 fill-current" aria-hidden="true" />
              )}
            </Button>
            <Button
              variant="ghost"
              size="icon"
              className="size-10 shrink-0 text-muted-foreground hover:text-foreground sm:size-9"
              onClick={() => handleSeek(progressPercent + SEEK_STEP_PERCENT * 2)}
              aria-label={tPlayer("seek")}
            >
              <SkipForward className="size-4" aria-hidden="true" />
            </Button>
          </div>

          {/* Right cluster: loop + speed */}
          <div className="flex items-center justify-end gap-1">
            <Button
              variant="ghost"
              size="icon"
              className={`size-9 shrink-0 sm:size-8 ${isLooping ? "text-foreground" : "text-muted-foreground hover:text-foreground"}`}
              onClick={toggleLoop}
              aria-label={isLooping ? tPlayer("loopOff") : tPlayer("loop")}
              aria-pressed={isLooping}
            >
              <Repeat className="size-4" aria-hidden="true" />
            </Button>

            <Button
              variant="ghost"
              size="sm"
              className={`h-8 gap-1.5 px-2 text-xs tabular-nums ${currentSpeed !== 1 ? "text-foreground" : "text-muted-foreground hover:text-foreground"}`}
              onClick={cycleSpeed}
              aria-label={tPlayer("speed")}
            >
              <Gauge className="size-3.5" aria-hidden="true" />
              {currentSpeed}x
            </Button>
          </div>
        </div>
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
      <div role="status" className="flex min-h-[7rem] items-center justify-center gap-2 text-sm text-muted-foreground">
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
