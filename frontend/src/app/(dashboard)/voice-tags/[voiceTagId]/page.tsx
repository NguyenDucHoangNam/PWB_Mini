"use client";

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { useProGuard } from "@/features/auth/hooks/use-pro-guard";
import { ProUpgradePrompt } from "@/features/voice/components/pro-upgrade-prompt";
import { VoiceTagPreview } from "@/features/voice/components/voice-tag-preview";
import { VoiceTagDeleteDialog } from "@/features/voice/components/voice-tag-delete-dialog";
import { useVoiceTag } from "@/features/voice/api/voice-tags";

export default function VoiceTagDetailPage() {
  const params = useParams();
  const router = useRouter();
  const voiceTagId = (params?.voiceTagId as string) ?? "";
  const { isPro } = useProGuard();
  const t = useTranslations("voice.voiceTags");
  const tActions = useTranslations("voice.actions");
  const tCommon = useTranslations("common");
  const tVoiceErrors = useTranslations("voice.errors");
  const [deleteOpen, setDeleteOpen] = useState(false);

  const { data, isLoading, isError } = useVoiceTag({ voiceTagId });

  if (!isPro) {
    return <ProUpgradePrompt />;
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center gap-3 p-12 text-sm text-neutral-500">
        <Spinner size="md" />
      </div>
    );
  }

  if (isError || !data?.data) {
    return (
      <div className="flex flex-col items-center gap-3 p-12 text-center">
        <p className="text-sm text-red-600 dark:text-red-400">{tVoiceErrors("voiceTagNotFound")}</p>
        <Button variant="outline" onClick={() => router.push("/voice-tags")}>
          {tActions("back")}
        </Button>
      </div>
    );
  }

  const tag = data.data;

  return (
    <div className="flex flex-col gap-6 font-sans">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
        <div className="flex flex-col gap-1">
          <h1 className="text-2xl font-bold tracking-tight text-black dark:text-white">
            {tag.name}
          </h1>
          <div className="flex flex-wrap items-center gap-2 text-xs text-neutral-500 dark:text-neutral-400">
            <span className="rounded-full border border-neutral-300 px-2 py-0.5 font-semibold uppercase dark:border-neutral-700">
              {tag.tagType === "TTS" ? t("type.TTS") : t("type.UPLOADED")}
            </span>
            <span>{t("duration", { seconds: tag.durationSeconds })}</span>
            {tag.languageCode && <span>{tag.languageCode}</span>}
          </div>
          {tag.description && (
            <p className="max-w-2xl text-sm text-neutral-600 dark:text-neutral-400">
              {tag.description}
            </p>
          )}
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => router.push("/voice-tags")}>
            {tActions("back")}
          </Button>
          <Button variant="destructive" onClick={() => setDeleteOpen(true)}>
            {tActions("delete")}
          </Button>
        </div>
      </div>

      <div className="rounded-xl border border-neutral-200 bg-white p-4 dark:border-neutral-800 dark:bg-black">
        <h2 className="mb-2 text-sm font-semibold text-black dark:text-white">{t("preview")}</h2>
        <VoiceTagPreview voiceTagId={tag.id} />
      </div>

      <VoiceTagDeleteDialog
        voiceTagId={tag.id}
        voiceTagName={tag.name}
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        onSuccess={() => router.push("/voice-tags")}
      />
    </div>
  );
}