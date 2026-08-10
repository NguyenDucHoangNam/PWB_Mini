"use client";

import { Film, ImageIcon } from "lucide-react";
import { useTranslations } from "next-intl";
import { cn } from "@/lib/utils";

export type StepMedia =
  | { kind: "image"; src: string }
  | { kind: "video"; src: string; poster?: string };

interface StepMediaFrameProps {
  slot: string;
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
        "neu-pressed overflow-hidden rounded-3xl bg-[#e0e5ec] p-3 dark:bg-[#1e222b] border-none",
        className,
      )}
    >
      {!media && (
        <div className="flex items-center justify-between gap-3 px-3 py-2">
          <span className="inline-flex items-center gap-2 font-mono text-xs font-bold uppercase tracking-[0.18em] text-indigo-600 dark:text-indigo-400">
            <Icon className="size-3.5" aria-hidden="true" />
            {slot}
          </span>
          <span className="font-mono text-xs font-semibold uppercase tracking-[0.18em] text-slate-400 dark:text-slate-500">
            {t("mediaPending")}
          </span>
        </div>
      )}

      {media ? (
        <div className="overflow-hidden rounded-3xl bg-slate-900 p-2 sm:p-2.5 shadow-xl shadow-slate-900/25 border border-slate-800 ring-1 ring-slate-900/40 transition-all">
          {media.kind === "image" ? (
            /* eslint-disable-next-line @next/next/no-img-element */
            <img
              src={media.src}
              alt={hint}
              loading="lazy"
              decoding="async"
              className="block w-full rounded-2xl border border-slate-700/50 shadow-md"
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
              className="block w-full rounded-2xl border border-slate-700/50 shadow-md"
            />
          )}
        </div>
      ) : (
        <div className="neu-raised-sm relative flex aspect-16/10 flex-col items-center justify-center gap-4 rounded-2xl bg-[#e0e5ec] p-6 text-center dark:bg-[#1e222b]">
          <span className="neu-pressed flex size-12 items-center justify-center rounded-2xl text-indigo-600 dark:text-indigo-400 bg-[#e0e5ec] dark:bg-[#1e222b]">
            <Icon className="size-5" aria-hidden="true" />
          </span>
          <figcaption className="max-w-sm text-sm font-medium leading-relaxed text-slate-600 dark:text-slate-300">
            {hint}
          </figcaption>
        </div>
      )}

      {media && (
        <figcaption className="px-4 py-3 text-sm font-medium leading-relaxed text-slate-600 dark:text-slate-300">
          {hint}
        </figcaption>
      )}
    </figure>
  );
}
