"use client";

import { useEffect, useRef, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { AlertCircle, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { asApiError } from "@/lib/api-client";
import { useProGuard } from "@/features/auth/hooks/use-pro-guard";
import { ProUpgradePrompt } from "@/features/voice/components/pro-upgrade-prompt";
import { SongDeleteDialog } from "@/features/voice/components/song-delete-dialog";
import { SongStatusBadge } from "@/features/voice/components/song-status-badge";
import { SongVoiceTagConfig } from "@/features/voice/components/song-voice-tag-config";
import { AudioPlayer } from "@/features/voice/components/audio-player";
import { useSong, useTriggerProcessing } from "@/features/voice/api/songs";
import { SONG_STREAM_KEY } from "@/features/voice/api/song-stream";
import { resolveVoiceErrorMessage } from "@/features/voice/lib/resolve-voice-error-message";
import type { SongStatus } from "@/features/voice/types";

/**
 * Processing runs off a queue, so the only way the page learns it finished is to ask again. React Query
 * pauses this while the tab is unfocused, which is why there is no explicit stop condition beyond the
 * status itself.
 */
const POLL_INTERVAL_MS = 3000;

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`;
}

function formatDuration(seconds: number) {
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${m}:${String(s).padStart(2, "0")}`;
}

function MetadataRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-3 py-1.5">
      <span className="text-xs font-medium text-neutral-500 dark:text-neutral-400">{label}</span>
      <span className="text-sm font-semibold text-black dark:text-white">{value}</span>
    </div>
  );
}

export default function SongDetailPage() {
  const params = useParams();
  const router = useRouter();
  const queryClient = useQueryClient();
  const songId = (params?.songId as string) ?? "";
  const { isPro } = useProGuard();
  const t = useTranslations("voice.songs.detail");
  const tActions = useTranslations("voice.actions");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("voice.errors");
  const [deleteOpen, setDeleteOpen] = useState(false);

  const { data: songRes, isLoading } = useSong({
    songId,
    queryConfig: {
      refetchInterval: (query) =>
        query.state.data?.data?.status === "PROCESSING" ? POLL_INTERVAL_MS : false,
    },
  });

  const status = songRes?.data?.status;

  // The presigned URL cached while the song was still rendering points at the ORIGINAL fallback. Once
  // the processed rendition exists that cache entry is stale, and nothing else would evict it.
  const previousStatus = useRef<SongStatus | undefined>(undefined);
  useEffect(() => {
    if (previousStatus.current === "PROCESSING" && status === "PROCESSED") {
      queryClient.invalidateQueries({ queryKey: [SONG_STREAM_KEY, songId] });
    }
    previousStatus.current = status;
  }, [status, songId, queryClient]);

  const { mutate: triggerProcessing, isPending: isTriggering } = useTriggerProcessing({
    mutationConfig: {
      onSuccess: (response) => {
        if (response.success) {
          toast.success(t("processingTriggered"));
        } else {
          toast.error(response.message || tCommon("error"));
        }
      },
      onError: asApiError((err) => {
        toast.error(resolveVoiceErrorMessage(err, tErrors, tCommon));
      }),
    },
  });

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

  if (!songRes?.data) {
    return (
      <div className="flex flex-col items-center gap-3 p-12 text-center">
        <p className="text-sm text-red-600 dark:text-red-400">{tErrors("songNotFound")}</p>
        <Button variant="outline" onClick={() => router.push("/dashboard/songs")}>
          {tActions("back")}
        </Button>
      </div>
    );
  }

  const song = songRes.data;

  return (
    <div className="flex flex-col gap-6 font-sans">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
        <div className="flex flex-col gap-1.5">
          <div className="flex items-center gap-2.5">
            <h1 className="text-2xl font-bold tracking-tight text-black dark:text-white">
              {song.title}
            </h1>
            <SongStatusBadge status={song.status} />
          </div>
          <div className="flex flex-wrap items-center gap-2 text-xs text-neutral-500 dark:text-neutral-400">
            {song.format && <span>{song.format.toUpperCase()}</span>}
            {song.fileSizeBytes !== null && <span>{formatBytes(song.fileSizeBytes)}</span>}
            {song.durationSeconds !== null && <span>{formatDuration(song.durationSeconds)}</span>}
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          {/* Also offered once processed: changing the tag settings is only meaningful if the song can
              be re-rendered with them. */}
          {song.status !== "PROCESSING" && (
            <Button
              disabled={isTriggering}
              onClick={() => triggerProcessing({ songId: song.id })}
            >
              {isTriggering && <Loader2 className="mr-2 size-4 animate-spin" aria-hidden="true" />}
              {song.status === "UPLOADED" ? t("triggerProcessing") : t("retryProcessing")}
            </Button>
          )}
          <Button variant="outline" onClick={() => router.push("/dashboard/songs")}>
            {tActions("back")}
          </Button>
          <Button variant="destructive" onClick={() => setDeleteOpen(true)}>
            {tActions("delete")}
          </Button>
        </div>
      </div>

      {song.status === "PROCESSING" && (
        <div
          role="status"
          className="flex items-center gap-3 rounded-xl border border-yellow-300 bg-yellow-50 px-4 py-3 text-sm text-yellow-800 dark:border-yellow-800 dark:bg-yellow-950/30 dark:text-yellow-300"
        >
          <Spinner size="sm" />
          <span>{t("processingBanner")}</span>
        </div>
      )}

      {song.status === "UPLOADED" && (
        <div className="flex items-start gap-3 rounded-xl border border-blue-300 bg-blue-50 px-4 py-3 text-sm text-blue-800 dark:border-blue-800 dark:bg-blue-950/30 dark:text-blue-300">
          <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          <span>{t("uploadedHint")}</span>
        </div>
      )}

      {song.status === "FAILED" && (
        <div
          role="alert"
          className="rounded-xl border border-red-300 bg-red-50 p-4 text-sm text-red-800 dark:border-red-800 dark:bg-red-950/30 dark:text-red-300"
        >
          <strong className="block font-semibold">{t("failedTitle")}</strong>
          <p className="mt-1 break-words">{song.lastError || t("failedUnknownReason")}</p>
        </div>
      )}

      <div className="grid gap-4 lg:grid-cols-3">
        <div className="grid gap-4 rounded-xl border border-neutral-200 bg-white p-4 md:grid-cols-2 lg:col-span-2 dark:border-neutral-800 dark:bg-black">
          <AudioPlayer songId={song.id} variant="ORIGINAL" />
          <AudioPlayer songId={song.id} variant="PROCESSED" />
        </div>

        <aside className="flex flex-col rounded-xl border border-neutral-200 bg-white p-4 lg:col-start-3 dark:border-neutral-800 dark:bg-black">
          <MetadataRow label={t("metadataFormat")} value={(song.format ?? "-").toUpperCase()} />
          <MetadataRow
            label={t("metadataDuration")}
            value={song.durationSeconds === null ? "-" : formatDuration(song.durationSeconds)}
          />
          <MetadataRow
            label={t("metadataFileSize")}
            value={song.fileSizeBytes === null ? "-" : formatBytes(song.fileSizeBytes)}
          />
          <MetadataRow
            label={t("metadataCreatedAt")}
            value={new Date(song.createdAt).toLocaleString()}
          />
        </aside>
      </div>

      <SongVoiceTagConfig songId={song.id} onSaved={() => toast.info(t("configSavedHint"))} />

      <SongDeleteDialog
        song={song}
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        onSuccess={() => router.push("/dashboard/songs")}
      />
    </div>
  );
}
