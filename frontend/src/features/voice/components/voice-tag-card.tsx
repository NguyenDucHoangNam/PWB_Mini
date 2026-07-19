"use client";

import Link from "next/link";
import { useState } from "react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import type { VoiceTag } from "../types";
import { useVoiceTagAudioUrl } from "../api/voice-tags";
import { VoiceTagDeleteDialog } from "./voice-tag-delete-dialog";

interface VoiceTagCardProps {
  voiceTag: VoiceTag;
}

export function VoiceTagCard({ voiceTag }: VoiceTagCardProps) {
  const t = useTranslations("voice.voiceTags");
  const tActions = useTranslations("voice.actions");
  const [deleteOpen, setDeleteOpen] = useState(false);

  return (
    <div className="flex flex-col gap-3 rounded-xl border border-neutral-200 bg-white p-4 dark:border-neutral-800 dark:bg-black">
      <div className="flex items-start justify-between gap-2">
        <div className="flex flex-col gap-1 min-w-0">
          <h3 className="truncate text-base font-semibold text-black dark:text-white">
            {voiceTag.name}
          </h3>
          <div className="flex items-center gap-2 text-xs text-neutral-500 dark:text-neutral-400">
            <span className="rounded-full border border-neutral-300 px-2 py-0.5 font-semibold uppercase dark:border-neutral-700">
              {voiceTag.tagType === "TTS" ? t("type.TTS") : t("type.UPLOADED")}
            </span>
            <span>{t("duration", { seconds: voiceTag.durationSeconds })}</span>
            {voiceTag.languageCode && <span>{voiceTag.languageCode}</span>}
          </div>
        </div>
        <div className="flex shrink-0 gap-2">
          <Link href={`/dashboard/voice-tags/${voiceTag.id}`}>
            <Button variant="outline" size="sm">
              {tActions("edit")}
            </Button>
          </Link>
          <Button variant="destructive" size="sm" onClick={() => setDeleteOpen(true)}>
            {tActions("delete")}
          </Button>
        </div>
      </div>

      {voiceTag.description && (
        <p className="line-clamp-2 text-sm text-neutral-600 dark:text-neutral-400">
          {voiceTag.description}
        </p>
      )}

      <VoiceTagPreviewInline voiceTagId={voiceTag.id} />

      <VoiceTagDeleteDialog
        voiceTagId={voiceTag.id}
        voiceTagName={voiceTag.name}
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
      />
    </div>
  );
}

function VoiceTagPreviewInline({ voiceTagId }: { voiceTagId: string }) {
  const t = useTranslations("voice.voiceTags");
  const { data, isLoading, error } = useVoiceTagAudioUrl({ voiceTagId });

  if (isLoading) {
    return (
      <div className="flex items-center gap-2 text-xs text-neutral-500">
        <Spinner size="sm" />
        {t("preview")}
      </div>
    );
  }

  if (error || !data?.data?.url) {
    return null;
  }

  return (
    <audio
      controls
      preload="metadata"
      src={data.data.url}
      className="w-full"
      aria-label={t("preview")}
    />
  );
}