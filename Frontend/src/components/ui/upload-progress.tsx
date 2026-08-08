"use client";

import { useTranslations } from "next-intl";
import { Loader2 } from "lucide-react";

/**
 * Where an upload has got to. The distinction between `uploading` and `finalizing` matters: once the
 * last byte is sent the server still has work to do — probing the audio, storing it, writing the row —
 * and a bar parked at 100% with nothing happening reads as a hang. `finalizing` keeps the bar full but
 * says out loud that the wait is now the server's.
 *
 * `merging` is the voice tag being rendered into the song. It is part of the same wait as far as the
 * user is concerned — the song is not the one they asked for until it finishes — so it is a phase of
 * this bar rather than a separate screen.
 */
export type UploadPhase = "preparing" | "uploading" | "finalizing" | "merging";

interface UploadProgressProps {
  phase: UploadPhase;
  /** 0–100, meaningful only while `uploading`. */
  percent: number;
  /** Supply both to show a "3.2 / 12.4 MB" readout; omit for uploads too small for it to mean anything. */
  loadedBytes?: number;
  totalBytes?: number;
  className?: string;
}

const MB = 1024 * 1024;

function toMb(bytes: number) {
  return (bytes / MB).toFixed(1);
}

export function UploadProgress({
  phase,
  percent,
  loadedBytes,
  totalBytes,
  className = "",
}: UploadProgressProps) {
  const t = useTranslations("upload");

  // A sliver of bar while preparing: zero width looks like nothing is happening at all.
  const width =
    phase === "preparing" ? 5 : phase === "finalizing" || phase === "merging" ? 100 : percent;
  const showSize = totalBytes !== undefined && loadedBytes !== undefined && totalBytes > 0;

  return (
    <div className={`flex flex-col gap-1.5 ${className}`}>
      <div
        role="progressbar"
        aria-valuemin={0}
        aria-valuemax={100}
        // Indeterminate while the server works: there is no honest number to report for that stretch.
        aria-valuenow={phase === "uploading" ? percent : undefined}
        className="relative h-2.5 w-full overflow-hidden rounded-full bg-muted"
      >
        <div
          className="absolute inset-y-0 left-0 rounded-full bg-primary transition-all duration-300 ease-out"
          style={{ width: `${width}%` }}
        />
        {phase !== "preparing" && (
          <div className="absolute inset-0 overflow-hidden rounded-full">
            <div className="h-full w-full animate-[shimmer_1.5s_infinite] bg-gradient-to-r from-transparent via-white/20 to-transparent" />
          </div>
        )}
      </div>

      <div className="flex items-center justify-between gap-2 text-xs text-muted-foreground">
        <span className="flex items-center gap-1.5">
          <Loader2 className="size-3 animate-spin" aria-hidden="true" />
          {phase === "preparing" && t("preparing")}
          {phase === "uploading" && t("sending", { percent })}
          {phase === "finalizing" && t("finalizing")}
          {phase === "merging" && t("merging")}
        </span>
        {phase === "uploading" && showSize && (
          <span className="font-medium tabular-nums">
            {t("sizeDetail", { loaded: toMb(loadedBytes), total: toMb(totalBytes) })}
          </span>
        )}
      </div>
    </div>
  );
}
