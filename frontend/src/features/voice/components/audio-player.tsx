"use client";

import { useTranslations } from "next-intl";
import { Spinner } from "@/components/ui/spinner";
import { usePresignedUrl } from "../hooks/use-presigned-url";
import { getOriginalUrl, getProcessedUrl, songStreamKey } from "../api/song-stream";
import type { AudioVariant } from "../types";

interface AudioPlayerProps {
  songId: string;
  /** Which rendition to play. Defaults to the watermarked one — that is what the song is for. */
  variant?: AudioVariant;
  label?: string;
}

export function AudioPlayer({ songId, variant = "PROCESSED", label }: AudioPlayerProps) {
  const t = useTranslations("voice.player");

  const wantsProcessed = variant === "PROCESSED";
  const query = usePresignedUrl({
    fetcher: () => (wantsProcessed ? getProcessedUrl({ songId }) : getOriginalUrl({ songId })),
    enabled: true,
    queryKey: songStreamKey(songId, wantsProcessed ? "processed" : "original"),
  });

  const heading = label ?? t(wantsProcessed ? "processedLabel" : "originalLabel");

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

  // Asking for the processed rendition before processing has finished serves the original instead. Say so,
  // otherwise a listener hears an untouched song and concludes the voice tag never worked.
  const servedOriginalInstead = wantsProcessed && query.data.data.variant === "ORIGINAL";

  return (
    <div className="flex flex-col gap-1">
      <span className="text-xs font-medium text-neutral-700 dark:text-neutral-300">{heading}</span>
      {servedOriginalInstead && (
        <span className="text-xs text-amber-600 dark:text-amber-400">{t("notReady")}</span>
      )}
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
