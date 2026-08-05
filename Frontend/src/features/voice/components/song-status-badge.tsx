"use client";

import { useTranslations } from "next-intl";
import { Loader2, Mic, Music } from "lucide-react";
import type { SongStatus } from "../types";

const BADGE_BASE =
  "inline-flex shrink-0 items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs font-semibold";

/**
 * Only the two states a listener can act on. `UPLOADED` and `PROCESSED` are both "ready to play" — the
 * difference between them is a detail of the merge job, not something to label a song with, so ready
 * songs carry no lifecycle badge at all. What actually distinguishes them is the voice tag, which
 * {@link SongVoiceTagBadge} shows instead.
 */
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
        className={`${BADGE_BASE} border-yellow-300 bg-yellow-50 text-yellow-800 dark:border-yellow-800 dark:bg-yellow-950/30 dark:text-yellow-300 ${className}`}
      >
        <Loader2 className="size-3 animate-spin" aria-hidden="true" />
        {t("processing")}
      </span>
    );
  }

  if (status === "FAILED") {
    return (
      <span
        className={`${BADGE_BASE} border-red-300 bg-red-50 text-red-800 dark:border-red-800 dark:bg-red-950/30 dark:text-red-300 ${className}`}
      >
        {t("failed")}
      </span>
    );
  }

  return null;
}

/**
 * Whether a song carries a voice tag. This is the real difference between two songs that are both ready,
 * so it earns the space a lifecycle badge used to take. Deliberately says only that one is present —
 * which tag it was belongs on the song's own page, not on every row of a listing.
 */
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
        className={`${BADGE_BASE} border-neutral-200 bg-neutral-50 text-neutral-600 dark:border-neutral-800 dark:bg-neutral-900 dark:text-neutral-400 ${className}`}
        title={t("plainMusicHint")}
      >
        <Music className="size-3" aria-hidden="true" />
        {t("plainMusic")}
      </span>
    );
  }

  return (
    <span
      className={`${BADGE_BASE} border-violet-300 bg-violet-50 text-violet-800 dark:border-violet-800 dark:bg-violet-950/30 dark:text-violet-300 ${className}`}
      title={t("hasVoiceTagHint")}
    >
      <Mic className="size-3 shrink-0" aria-hidden="true" />
      {t("hasVoiceTag")}
    </span>
  );
}
