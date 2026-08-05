"use client";

import { memo, useRef, useState } from "react";
import { UserAvatar } from "../ui/user-avatar";
import { WAVEFORM_BAR_COUNT, type WaveformState } from "../../hooks/use-waveform-peaks";
import { useLiveroomStore } from "../../stores/use-liveroom-store";
import { displayName } from "../../utils/participant-sort";
import type { TrackComment } from "../../types";

const IDLE_PEAKS = Array.from({ length: WAVEFORM_BAR_COUNT }, (_, index) =>
  0.18 + 0.12 * Math.abs(Math.sin(index / 3)),
);

function formatClock(totalSeconds: number): string {
  const safe = Math.max(0, Math.floor(totalSeconds));
  return `${Math.floor(safe / 60)}:${String(safe % 60).padStart(2, "0")}`;
}




const Bars = memo(function Bars({ peaks, className }: { peaks: number[]; className: string }) {
  return (
    <div className={`flex size-full items-center gap-px ${className}`}>
      {peaks.map((peak, index) => (
        <span
          key={index}
          className="min-w-px flex-1 rounded-[1px] bg-current"
          style={{ height: `${Math.max(6, peak * 100)}%` }}
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
  const peaks = waveform.peaks ?? IDLE_PEAKS;
  const pulsing = waveform.status === "loading";
  const participants = useLiveroomStore((state) => state.participants);
  const trackRef = useRef<HTMLDivElement>(null);
  const [hoverRatio, setHoverRatio] = useState<number | null>(null);

  const seekable = durationSeconds > 0;
  const playedPercent = seekable ? Math.min(100, (position / durationSeconds) * 100) : 0;

  const ratioAt = (clientX: number): number | null => {
    const box = trackRef.current?.getBoundingClientRect();
    if (!box || box.width === 0) return null;
    return Math.min(1, Math.max(0, (clientX - box.left) / box.width));
  };

  const seekTo = (clientX: number) => {
    if (!seekable) return;
    const ratio = ratioAt(clientX);
    if (ratio === null) return;
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
        aria-valuenow={Math.round(position)}
        aria-valuetext={formatClock(position)}
        onClick={(event) => seekTo(event.clientX)}
        onMouseMove={(event) => setHoverRatio(ratioAt(event.clientX))}
        onMouseLeave={() => setHoverRatio(null)}
        onKeyDown={(event) => {
          if (!seekable) return;
          if (event.key === "ArrowRight") onSeek(Math.min(durationSeconds, position + 5));
          if (event.key === "ArrowLeft") onSeek(Math.max(0, position - 5));
        }}
        className={`relative h-14 select-none outline-none focus-visible:ring-2 focus-visible:ring-ring/50 md:h-16 ${
          seekable ? "cursor-pointer" : "cursor-default"
        }`}
      >
        <div className="absolute inset-0 text-neutral-300 dark:text-neutral-700">
          <Bars peaks={peaks} className={pulsing ? "animate-pulse" : ""} />
        </div>

        <div
          className="absolute inset-0 text-black dark:text-white"
          style={{ clipPath: `inset(0 ${100 - playedPercent}% 0 0)` }}
        >
          <Bars peaks={peaks} className="" />
        </div>

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