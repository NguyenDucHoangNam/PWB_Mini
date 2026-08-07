"use client";

import { useTranslations } from "next-intl";
import { Mic } from "lucide-react";
import type { SongStatus } from "../types";

const BADGE_BASE =
  "inline-flex shrink-0 items-center gap-1.5 rounded-md border px-2 py-0.5 text-xs font-medium";

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
        className={`${BADGE_BASE} border-border bg-secondary text-secondary-foreground ${className}`}
      >
        <span className="waveform" aria-hidden="true">
          <span />
          <span />
          <span />
        </span>
        {t("processing")}
      </span>
    );
  }

  if (status === "FAILED") {
    return (
      <span
        className={`${BADGE_BASE} border-destructive/30 bg-destructive/10 text-destructive ${className}`}
      >
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
        className={`${BADGE_BASE} border-transparent text-muted-foreground ${className}`}
        title={t("plainMusicHint")}
      >
        {t("plainMusic")}
      </span>
    );
  }

  return (
    <span
      className={`${BADGE_BASE} border-border bg-secondary text-secondary-foreground ${className}`}
      title={t("hasVoiceTagHint")}
    >
      <Mic className="size-3" aria-hidden="true" />
      {t("hasVoiceTag")}
    </span>
  );
}
