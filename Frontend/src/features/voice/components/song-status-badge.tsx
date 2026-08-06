"use client";

import { useTranslations } from "next-intl";
import { Mic, Music } from "lucide-react";
import type { SongStatus } from "../types";

const BADGE_BASE =
  "inline-flex shrink-0 items-center gap-1.5 rounded-md border px-2 py-0.5 text-[11px] font-mono tracking-tight";

export function SongStatusBadge({
  status,
  className = "",
}: {
  status: SongStatus;
  className?: string;
}) {
  const t = useTranslations("voice.status");

  if (status === "PROCESSING") {
    return (
      <span
        className={`${BADGE_BASE} border-neutral-300 bg-neutral-100 text-neutral-800 dark:border-neutral-700 dark:bg-neutral-900 dark:text-neutral-200 ${className}`}
      >
        <span className="flex items-end gap-0.5 h-3 w-3 shrink-0" aria-hidden="true">
          <span className="w-0.5 bg-neutral-800 dark:bg-neutral-200 animate-[bounce_1s_infinite_100ms] h-full rounded-full" />
          <span className="w-0.5 bg-neutral-800 dark:bg-neutral-200 animate-[bounce_1s_infinite_300ms] h-2/3 rounded-full" />
          <span className="w-0.5 bg-neutral-800 dark:bg-neutral-200 animate-[bounce_1s_infinite_200ms] h-4/5 rounded-full" />
        </span>
        {t("processing")}
      </span>
    );
  }

  if (status === "FAILED") {
    return (
      <span
        className={`${BADGE_BASE} border-dashed border-neutral-400 bg-neutral-100 text-neutral-700 dark:border-neutral-600 dark:bg-neutral-900 dark:text-neutral-300 ${className}`}
      >
        <span className="size-1.5 rounded-full bg-neutral-500" aria-hidden="true" />
        {t("failed")}
      </span>
    );
  }

  return null;
}

export function SongVoiceTagBadge({
  hasVoiceTag,
  className = "",
}: {
  hasVoiceTag: boolean;
  className?: string;
}) {
  const t = useTranslations("voice.status");

  if (!hasVoiceTag) {
    return (
      <span
        className={`${BADGE_BASE} border-neutral-200 bg-neutral-50 text-neutral-500 dark:border-neutral-800 dark:bg-neutral-950 dark:text-neutral-400 ${className}`}
        title={t("plainMusicHint")}
      >
        <Music className="size-3" aria-hidden="true" />
        {t("plainMusic")}
      </span>
    );
  }

  return (
    <span
      className={`${BADGE_BASE} border-neutral-900 bg-black text-white dark:border-neutral-100 dark:bg-white dark:text-black ${className}`}
      title={t("hasVoiceTagHint")}
    >
      <Mic className="size-3 shrink-0" aria-hidden="true" />
      {t("hasVoiceTag")}
    </span>
  );
}
