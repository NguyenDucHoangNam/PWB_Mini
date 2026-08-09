"use client";

import { Film, ImageIcon } from "lucide-react";
import { useTranslations } from "next-intl";
import { cn } from "@/lib/utils";

/**
 * The illustration attached to a step. A step always declares its slot; `null` renders a labelled
 * placeholder instead of nothing, so a capture that has not been recorded yet is visible on the page
 * rather than silently missing from it.
 */
export type StepMedia =
  | { kind: "image"; src: string }
  | { kind: "video"; src: string; poster?: string };

interface StepMediaFrameProps {
  /** Stable id printed on the frame, so a recorded file can be matched back to its step. */
  slot: string;
  /** What the capture is meant to show. Doubles as the alt text once the file exists. */
  hint: string;
  media?: StepMedia | null;
  className?: string;
}

export function StepMediaFrame({ slot, hint, media, className }: StepMediaFrameProps) {
  const t = useTranslations("features.walkthrough");
  const Icon = media?.kind === "video" ? Film : ImageIcon;

  return (
    <figure
      className={cn(
        "overflow-hidden rounded-2xl border bg-card",
        media ? "border-border" : "border-dashed border-border",
        className,
      )}
    >
      <div className="flex items-center justify-between gap-3 border-b border-border px-4 py-2.5">
        <span className="inline-flex items-center gap-2 font-mono text-[0.68rem] uppercase tracking-[0.18em] text-muted-foreground">
          <Icon className="size-3.5" aria-hidden="true" />
          {slot}
        </span>
        {!media && (
          <span className="font-mono text-[0.68rem] uppercase tracking-[0.18em] text-muted-foreground/70">
            {t("mediaPending")}
          </span>
        )}
      </div>

      {media ? (
        <div className="bg-muted/30">
          {media.kind === "image" ? (
            /* eslint-disable-next-line @next/next/no-img-element */
            <img
              src={media.src}
              alt={hint}
              loading="lazy"
              decoding="async"
              className="block w-full"
            />
          ) : (
            <video
              src={media.src}
              poster={media.poster}
              controls
              loop
              muted
              playsInline
              preload="metadata"
              aria-label={hint}
              className="block w-full"
            />
          )}
        </div>
      ) : (
        <div
          className="relative flex aspect-16/10 flex-col items-center justify-center gap-4 px-6 text-center"
          /* The faint grid reads as "a screen goes here" without pretending to be one. */
          style={{
            backgroundImage:
              "linear-gradient(to right, var(--border) 1px, transparent 1px), linear-gradient(to bottom, var(--border) 1px, transparent 1px)",
            backgroundSize: "34px 34px",
          }}
        >
          <span className="flex size-11 items-center justify-center rounded-xl border border-border bg-background text-muted-foreground">
            <Icon className="size-5" aria-hidden="true" />
          </span>
          <figcaption className="max-w-sm text-sm leading-relaxed text-muted-foreground">
            {hint}
          </figcaption>
        </div>
      )}

      {media && (
        <figcaption className="border-t border-border px-4 py-3 text-sm leading-relaxed text-muted-foreground">
          {hint}
        </figcaption>
      )}
    </figure>
  );
}
