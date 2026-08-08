"use client";

import {
  memo,
  useEffect,
  useMemo,
  useRef,
  useState,
  type PointerEvent as ReactPointerEvent,
} from "react";
import { UserAvatar } from "../ui/user-avatar";
import { WAVEFORM_BAR_COUNT, type WaveformState } from "../../hooks/use-waveform-peaks";
import { useLiveroomStore } from "../../stores/use-liveroom-store";
import { displayName } from "../../utils/participant-sort";
import type { TrackComment } from "../../types";

const IDLE_PEAKS = Array.from({ length: WAVEFORM_BAR_COUNT }, (_, index) =>
  0.3 + 0.2 * Math.abs(Math.sin(index / 9)) + 0.12 * Math.abs(Math.sin(index / 2.3)),
);

const BAR_PITCH_PX = 3;
const MIN_BAR_COUNT = 24;
const AMPLITUDE_CURVE = 0.62;
const MIN_BAR_PERCENT = 12;

function formatClock(totalSeconds: number): string {
  const safe = Math.max(0, Math.floor(totalSeconds));
  return `${Math.floor(safe / 60)}:${String(safe % 60).padStart(2, "0")}`;
}

function resample(peaks: number[], count: number): number[] {
  if (count >= peaks.length) return peaks;
  const step = peaks.length / count;
  const out: number[] = [];
  for (let bar = 0; bar < count; bar += 1) {
    const start = Math.floor(bar * step);
    const end = Math.max(start + 1, Math.min(peaks.length, Math.floor((bar + 1) * step)));
    let loudest = 0;
    for (let index = start; index < end; index += 1) {
      if (peaks[index] > loudest) loudest = peaks[index];
    }
    out.push(loudest);
  }
  return out;
}

function barHeight(peak: number): string {
  const scaled = Math.pow(Math.min(1, Math.max(0, peak)), AMPLITUDE_CURVE) * 100;
  return `${Math.max(MIN_BAR_PERCENT, scaled)}%`;
}

const Bars = memo(function Bars({
  peaks,
  className,
  barClassName,
}: {
  peaks: number[];
  className: string;
  barClassName: string;
}) {
  return (
    <div className={`flex size-full items-center gap-px ${className}`}>
      {peaks.map((peak, index) => (
        <span
          key={index}
          className={`min-w-0 flex-1 rounded-full ${barClassName}`}
          style={{ height: barHeight(peak) }}
        />
      ))}
    </div>
  );
});

export function TrackWaveform({
  waveform,
  position,
  durationSeconds,
  comments,
  pinnedPosition,
  onSeek,
}: {
  waveform: WaveformState;
  position: number;
  durationSeconds: number;
  comments: TrackComment[];
  pinnedPosition: number | null;
  onSeek: (positionSeconds: number) => void;
}) {
  const pulsing = waveform.status === "loading";
  const participants = useLiveroomStore((state) => state.participants);
  const trackRef = useRef<HTMLDivElement>(null);
  const [hoverRatio, setHoverRatio] = useState<number | null>(null);
  const [scrubRatio, setScrubRatio] = useState<number | null>(null);
  const [trackWidth, setTrackWidth] = useState(0);

  useEffect(() => {
    const element = trackRef.current;
    if (!element) return;
    setTrackWidth(element.getBoundingClientRect().width);
    const observer = new ResizeObserver((entries) => {
      setTrackWidth(entries[0].contentRect.width);
    });
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  const source = waveform.peaks ?? IDLE_PEAKS;
  const peaks = useMemo(
    () => resample(source, Math.max(MIN_BAR_COUNT, Math.floor(trackWidth / BAR_PITCH_PX))),
    [source, trackWidth],
  );

  const seekable = durationSeconds > 0;
  const playedRatio = seekable ? Math.min(1, Math.max(0, position / durationSeconds)) : 0;
  const displayRatio = scrubRatio ?? playedRatio;
  const displayPercent = displayRatio * 100;
  const displaySeconds = displayRatio * durationSeconds;

  const ratioAt = (clientX: number): number | null => {
    const box = trackRef.current?.getBoundingClientRect();
    if (!box || box.width === 0) return null;
    return Math.min(1, Math.max(0, (clientX - box.left) / box.width));
  };

  const handlePointerDown = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (!seekable || event.button !== 0) return;
    event.currentTarget.setPointerCapture(event.pointerId);
    setScrubRatio(ratioAt(event.clientX));
  };

  const handlePointerMove = (event: ReactPointerEvent<HTMLDivElement>) => {
    const ratio = ratioAt(event.clientX);
    setHoverRatio(ratio);
    if (scrubRatio !== null) setScrubRatio(ratio);
  };

  const handlePointerUp = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (scrubRatio === null) return;
    const ratio = ratioAt(event.clientX) ?? scrubRatio;
    setScrubRatio(null);
    onSeek(ratio * durationSeconds);
  };

  return (
    <div className="flex flex-col gap-1">
      <div
        ref={trackRef}
        role="slider"
        tabIndex={seekable ? 0 : -1}
        aria-label="waveform"
        aria-valuemin={0}
        aria-valuemax={Math.round(durationSeconds)}
        aria-valuenow={Math.round(displaySeconds)}
        aria-valuetext={formatClock(displaySeconds)}
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onPointerCancel={() => setScrubRatio(null)}
        onPointerLeave={() => setHoverRatio(null)}
        onKeyDown={(event) => {
          if (!seekable) return;
          if (event.key === "ArrowRight") onSeek(Math.min(durationSeconds, position + 5));
          if (event.key === "ArrowLeft") onSeek(Math.max(0, position - 5));
        }}
        className={`relative h-16 touch-none rounded-lg select-none outline-none focus-visible:ring-2 focus-visible:ring-ring/50 md:h-20 ${
          seekable ? "cursor-pointer" : "cursor-default"
        }`}
      >
        <div className="absolute inset-0">
          <Bars
            peaks={peaks}
            className={pulsing ? "animate-pulse" : ""}
            barClassName="bg-neutral-300 dark:bg-neutral-700"
          />
        </div>

        <div
          className="absolute inset-0"
          style={{ clipPath: `inset(0 ${100 - displayPercent}% 0 0)` }}
        >
          <Bars
            peaks={peaks}
            className=""
            barClassName="bg-gradient-to-t from-neutral-900 via-neutral-700 to-neutral-500 dark:from-white dark:via-neutral-200 dark:to-neutral-400"
          />
        </div>

        {seekable ? (
          <span
            aria-hidden
            className="pointer-events-none absolute inset-y-1 w-0.5 -translate-x-1/2 rounded-full bg-black dark:bg-white"
            style={{ left: `${displayPercent}%` }}
          >
            <span className="absolute -top-1 left-1/2 size-2 -translate-x-1/2 rounded-full bg-black ring-2 ring-white dark:bg-white dark:ring-neutral-950" />
          </span>
        ) : null}

        {pinnedPosition !== null && seekable ? (
          <span
            aria-hidden
            className="pointer-events-none absolute inset-y-0 w-0.5 bg-red-500"
            style={{ left: `${(pinnedPosition / durationSeconds) * 100}%` }}
          />
        ) : null}

        {hoverRatio !== null && seekable ? (
          <>
            <span
              aria-hidden
              className="pointer-events-none absolute inset-y-0 w-px bg-neutral-400/70"
              style={{ left: `${hoverRatio * 100}%` }}
            />
            <span
              aria-hidden
              className="pointer-events-none absolute -top-1 -translate-x-1/2 rounded bg-black px-1.5 py-0.5 text-[10px] font-semibold tabular-nums text-white dark:bg-white dark:text-black"
              style={{ left: `${hoverRatio * 100}%` }}
            >
              {formatClock(hoverRatio * durationSeconds)}
            </span>
          </>
        ) : null}
      </div>

      <div className="relative h-6">
        {seekable
          ? comments.map((comment) => (
              <div
                key={comment.id}
                className="group absolute top-0 -translate-x-1/2"
                style={{ left: `${(comment.positionSeconds / durationSeconds) * 100}%` }}
              >
                <button
                  type="button"
                  aria-label={`${displayName(comment)} · ${formatClock(comment.positionSeconds)}`}
                  onClick={() => onSeek(comment.positionSeconds)}
                  className="block transition-transform hover:scale-125"
                >
                  <UserAvatar
                    email={comment.userEmail}
                    avatarUrl={participants[comment.userId]?.avatarUrl ?? null}
                    seed={comment.userId}
                    className="size-5 text-[8px] ring-2 ring-white dark:ring-neutral-950"
                  />
                </button>
                <span className="pointer-events-none absolute bottom-full left-1/2 z-20 mb-1 hidden -translate-x-1/2 rounded-md bg-black px-2 py-1 text-xs whitespace-nowrap text-white group-hover:block dark:bg-white dark:text-black">
                  <span className="font-semibold">{displayName(comment)}</span>{" "}
                  {comment.content}
                </span>
              </div>
            ))
          : null}
      </div>
    </div>
  );
}
