"use client";

import { useTranslations } from "next-intl";
import { Spinner } from "@/components/ui/spinner";
import { usePresignedUrl } from "../hooks/use-presigned-url";
import { getOriginalUrl, getStreamUrl, songStreamKey } from "../api/song-stream";
import type { SongStatus } from "../types";

interface AudioPlayerProps {
  songId: string;
  variant: "original" | "processed";
  status?: SongStatus;
  label?: string;
}

export function AudioPlayer({ songId, variant, status, label }: AudioPlayerProps) {
  const t = useTranslations("voice.player");
  const tStatus = useTranslations("voice.status");

  const enabled = variant === "original" || status === "PROCESSED";

  const query = usePresignedUrl({
    fetcher:
      variant === "processed"
        ? () => getStreamUrl({ songId })
        : () => getOriginalUrl({ songId }),
    enabled,
    queryKey: songStreamKey(songId, variant),
  });

  if (variant === "processed" && status !== "PROCESSED") {
    return (
      <div className="rounded-lg border border-dashed border-neutral-300 p-4 text-center text-xs text-neutral-500 dark:border-neutral-700 dark:text-neutral-400">
        {label ?? t("processedLabel")} - {t("notReady")} ({tStatus(status?.toLowerCase() as never ?? "uploaded")})
      </div>
    );
  }

  if (query.isLoading) {
    return (
      <div className="flex items-center gap-2 text-sm text-neutral-500">
        <Spinner size="sm" />
        {label ?? (variant === "original" ? t("originalLabel") : t("processedLabel"))}
      </div>
    );
  }

  if (query.isError || !query.data?.data?.url) {
    return (
      <div className="text-sm text-red-600 dark:text-red-400">{t("loadError")}</div>
    );
  }

  return (
    <div className="flex flex-col gap-1">
      {label && <span className="text-xs font-medium text-neutral-700 dark:text-neutral-300">{label}</span>}
      <audio
        controls
        preload="metadata"
        src={query.data.data.url}
        className="w-full"
        aria-label={label ?? (variant === "original" ? t("originalLabel") : t("processedLabel"))}
      />
    </div>
  );
}