"use client";

import { useTranslations } from "next-intl";
import { Mic } from "lucide-react";
import { NEU_TEXT_MUTED, NeuBadge } from "@/components/ui/neu";
import type { SongStatus } from "../types";

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
      <NeuBadge tone="accent" className={className}>
        <span className="waveform" aria-hidden="true">
          <span />
          <span />
          <span />
        </span>
        {t("processing")}
      </NeuBadge>
    );
  }

  if (status === "FAILED") {
    return (
      <NeuBadge tone="danger" className={className}>
        {t("failed")}
      </NeuBadge>
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

  // "No voice tag" is the absence of a thing, so it gets no chrome at all —
  // only the tagged state is worth lifting off the surface.
  if (!hasVoiceTag) {
    return (
      <span
        className={`inline-flex shrink-0 items-center text-xs font-medium ${NEU_TEXT_MUTED} ${className}`}
        title={t("plainMusicHint")}
      >
        {t("plainMusic")}
      </span>
    );
  }

  return (
    <NeuBadge tone="muted" className={className} title={t("hasVoiceTagHint")}>
      <Mic className="size-3" aria-hidden="true" />
      {t("hasVoiceTag")}
    </NeuBadge>
  );
}
