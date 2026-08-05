"use client";

import { useTranslations } from "next-intl";
import { Spinner } from "@/components/ui/spinner";
import { usePresignedUrl } from "../hooks/use-presigned-url";
import { getSongAudioUrl, songStreamKey } from "../api/song-stream";

interface AudioPlayerProps {
  songId: string;
  label?: string;
}

export function AudioPlayer({ songId, label }: AudioPlayerProps) {
  const t = useTranslations("voice.player");

  const query = usePresignedUrl({
    fetcher: () => getSongAudioUrl({ songId }),
    enabled: true,
    queryKey: songStreamKey(songId),
  });

  const heading = label ?? t("songLabel");

  if (query.isLoading) {
    return (
      <div className="flex items-center gap-2 text-sm text-neutral-500">
        <Spinner size="sm" />
        {heading}
      </div>
    );
  }

  if (query.isError || !query.data?.data?.url) {
    return (
      <div className="flex flex-col gap-1">
        <span className="text-xs font-medium text-neutral-700 dark:text-neutral-300">{heading}</span>
        <span className="text-sm text-red-600 dark:text-red-400">{t("loadError")}</span>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-1">
      <span className="text-xs font-medium text-neutral-700 dark:text-neutral-300">{heading}</span>
      <audio
        controls
        preload="metadata"
        src={query.data.data.url}
        className="w-full"
        aria-label={heading}
      />
    </div>
  );
}
