"use client";

import { useTranslations } from "next-intl";
import { Spinner } from "@/components/ui/spinner";
import { usePresignedUrl } from "../hooks/use-presigned-url";
import { getVoiceTagAudioUrl } from "../api/voice-tags";

interface VoiceTagPreviewProps {
  voiceTagId: string;
}

export function VoiceTagPreview({ voiceTagId }: VoiceTagPreviewProps) {
  const t = useTranslations("voice.voiceTags");
  const tPlayer = useTranslations("voice.player");

  const query = usePresignedUrl({
    fetcher: () => getVoiceTagAudioUrl({ voiceTagId }),
    enabled: Boolean(voiceTagId),
    queryKey: ["voice-voice-tags", voiceTagId, "audio"],
  });

  if (query.isLoading) {
    return (
      <div className="flex items-center gap-2 text-sm text-neutral-500">
        <Spinner size="sm" />
        {t("preview")}
      </div>
    );
  }

  if (query.isError || !query.data?.data?.url) {
    return (
      <div className="text-sm text-red-600 dark:text-red-400">{tPlayer("loadError")}</div>
    );
  }

  return (
    <audio
      controls
      preload="metadata"
      src={query.data.data.url}
      className="w-full"
      aria-label={t("preview")}
    />
  );
}