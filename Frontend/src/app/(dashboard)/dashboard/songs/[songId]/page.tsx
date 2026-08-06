"use client";

import { useEffect, useRef, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { ArrowLeft, Loader2, Trash2, Pencil, Check, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { useProGuard } from "@/features/auth/hooks/use-pro-guard";
import { ProUpgradePrompt } from "@/features/voice/components/pro-upgrade-prompt";
import { SongDeleteDialog } from "@/features/voice/components/song-delete-dialog";
import { SongStatusBadge, SongVoiceTagBadge } from "@/features/voice/components/song-status-badge";
import { AudioPlayer } from "@/features/voice/components/audio-player";
import { useSong, useRetryProcessing, useVoiceTagConfig, useUpdateSong } from "@/features/voice/api/songs";
import { SONG_STREAM_KEY } from "@/features/voice/api/song-stream";
import { asApiError } from "@/lib/api-client";
import { resolveVoiceErrorMessage } from "@/features/voice/lib/resolve-voice-error-message";
import type { SongStatus } from "@/features/voice/types";

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

export default function SongDetailPage() {
  const params = useParams();
  const router = useRouter();
  const queryClient = useQueryClient();
  const songId = (params?.songId as string) ?? "";
  const { isPro } = useProGuard();
  const t = useTranslations("voice.songs.detail");
  const tConfig = useTranslations("voice.config");
  const tActions = useTranslations("voice.actions");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("voice.errors");
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [isEditingTitle, setIsEditingTitle] = useState(false);
  const [editTitle, setEditTitle] = useState("");
  const titleInputRef = useRef<HTMLInputElement>(null);

  const { data: songRes, isLoading } = useSong({
    songId,
    queryConfig: {
      refetchIntervalInBackground: true,
      refetchInterval: (query) =>
        query.state.data?.data?.status === "PROCESSING" ? POLL_INTERVAL_MS : false,
    },
  });

  const { data: configRes } = useVoiceTagConfig({ songId });
  const voiceTagConfig = configRes?.data ?? null;

  const status = songRes?.data?.status;

  const previousStatus = useRef<SongStatus | undefined>(undefined);
  useEffect(() => {
    if (!songId || !status) return;

    const wasMerging = previousStatus.current === "PROCESSING";
    previousStatus.current = status;

    if (!wasMerging) return;

    if (status === "PROCESSED") {
      queryClient.invalidateQueries({ queryKey: [SONG_STREAM_KEY, songId] });
      toast.success(t("processingCompleted"));
    } else if (status === "FAILED") {
      toast.error(t("processingFailedToast"));
    }
  }, [status, songId, queryClient, t]);

  const { mutate: retryProcessing, isPending: isRetrying } = useRetryProcessing({
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

  const { mutate: updateSong, isPending: isSaving } = useUpdateSong({
    mutationConfig: {
      onSuccess: (response) => {
        if (response.success) {
          toast.success(tCommon("save"));
          setIsEditingTitle(false);
        } else {
          toast.error(response.message || tCommon("error"));
        }
      },
      onError: asApiError((err) => {
        toast.error(resolveVoiceErrorMessage(err, tErrors, tCommon));
      }),
    },
  });

  const startEditTitle = () => {
    setEditTitle(song.title);
    setIsEditingTitle(true);
    setTimeout(() => titleInputRef.current?.select(), 0);
  };

  const cancelEditTitle = () => {
    setEditTitle(song.title);
    setIsEditingTitle(false);
  };

  const saveTitle = () => {
    const trimmed = editTitle.trim();
    if (!trimmed || isSaving) return;
    if (trimmed === song.title) {
      setIsEditingTitle(false);
      return;
    }
    updateSong({ songId: song.id, data: { title: trimmed } });
  };

  const handleTitleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter") {
      e.preventDefault();
      saveTitle();
    } else if (e.key === "Escape") {
      cancelEditTitle();
    }
  };

  return (
    <div className="flex flex-col gap-5 font-sans">
      <button
        type="button"
        onClick={() => router.push("/dashboard/songs")}
        className="flex items-center gap-1.5 self-start text-xs font-medium text-neutral-500 transition-colors hover:text-black dark:text-neutral-400 dark:hover:text-white"
      >
        <ArrowLeft className="size-3.5" aria-hidden="true" />
        {tActions("back")}
      </button>

      <div className="flex flex-col gap-1.5 sm:flex-row sm:items-start sm:justify-between">
        <div className="flex flex-col gap-1.5 min-w-0">
          <div className="flex items-center gap-2.5">
            {isEditingTitle ? (
              <div className="flex items-center gap-2 min-w-0 flex-1">
                <input
                  ref={titleInputRef}
                  type="text"
                  value={editTitle}
                  onChange={(e) => setEditTitle(e.target.value)}
                  onKeyDown={handleTitleKeyDown}
                  onBlur={cancelEditTitle}
                  maxLength={200}
                  disabled={isSaving}
                  className="min-w-0 flex-1 rounded-lg border border-neutral-300 bg-white px-3 py-1 text-2xl font-bold tracking-tight text-black outline-none focus:border-black dark:border-neutral-700 dark:bg-neutral-900 dark:text-white dark:focus:border-white"
                />
                <button
                  type="button"
                  onMouseDown={(e) => {
                    e.preventDefault();
                    saveTitle();
                  }}
                  disabled={isSaving || !editTitle.trim()}
                  className="flex size-8 shrink-0 items-center justify-center rounded-lg text-neutral-600 hover:bg-neutral-100 dark:text-neutral-400 dark:hover:bg-neutral-800"
                  aria-label={tCommon("save")}
                >
                  <Check className="size-4" />
                </button>
                <button
                  type="button"
                  onMouseDown={(e) => {
                    e.preventDefault();
                    cancelEditTitle();
                  }}
                  className="flex size-8 shrink-0 items-center justify-center rounded-lg text-neutral-600 hover:bg-neutral-100 dark:text-neutral-400 dark:hover:bg-neutral-800"
                  aria-label={tCommon("cancel")}
                >
                  <X className="size-4" />
                </button>
              </div>
            ) : (
              <>
                <h1 className="text-2xl font-bold tracking-tight text-black dark:text-white truncate">
                  {song.title}
                </h1>
                <button
                  type="button"
                  onClick={startEditTitle}
                  className="flex size-7 shrink-0 items-center justify-center rounded-lg text-neutral-400 transition-colors hover:bg-neutral-100 hover:text-black dark:text-neutral-500 dark:hover:bg-neutral-800 dark:hover:text-white"
                  aria-label={tActions("edit")}
                >
                  <Pencil className="size-3.5" />
                </button>
                <SongStatusBadge status={song.status} />
              </>
            )}
          </div>
          <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-neutral-500 dark:text-neutral-400">
            <SongVoiceTagBadge hasVoiceTag={song.hasVoiceTag} />
            {song.format && <span>{song.format.toUpperCase()}</span>}
            {song.fileSizeBytes !== null && <span>{formatBytes(song.fileSizeBytes)}</span>}
            {song.durationSeconds !== null && <span>{formatDuration(song.durationSeconds)}</span>}
            <span>{new Date(song.createdAt).toLocaleDateString()}</span>
          </div>
        </div>
        <div className="flex shrink-0 items-center gap-2 mt-2 sm:mt-0">
          {song.status === "FAILED" && (
            <Button size="sm" disabled={isRetrying} onClick={() => retryProcessing({ songId: song.id })}>
              {isRetrying && <Loader2 className="mr-2 size-3.5 animate-spin" aria-hidden="true" />}
              {t("retryProcessing")}
            </Button>
          )}
          <Button
            variant="outline"
            size="icon"
            className="size-8"
            onClick={() => setDeleteOpen(true)}
            aria-label={tActions("delete")}
          >
            <Trash2 className="size-3.5" />
          </Button>
        </div>
      </div>

      {song.status === "PROCESSING" && (
        <div
          role="status"
          className="flex items-center gap-3 rounded-xl border border-neutral-200 bg-neutral-50 px-4 py-3 text-sm text-neutral-700 dark:border-neutral-800 dark:bg-neutral-900 dark:text-neutral-300"
        >
          <Spinner size="sm" />
          <span>{t("processingBanner")}</span>
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

      <div className="rounded-xl border border-neutral-200 bg-white px-5 py-4 dark:border-neutral-800 dark:bg-black">
        <AudioPlayer songId={song.id} />
      </div>

      <section className="rounded-xl border border-neutral-200 bg-white dark:border-neutral-800 dark:bg-black overflow-hidden">
        <div className="flex items-center justify-between border-b border-neutral-100 px-5 py-3 dark:border-neutral-800/50">
          <h2 className="text-sm font-semibold text-neutral-900 dark:text-neutral-100">
            {tConfig("title")}
          </h2>
          {voiceTagConfig && (
            <span className="rounded-full bg-black px-3 py-0.5 text-xs font-semibold text-white dark:bg-white dark:text-black">
              {voiceTagConfig.voiceTagName ?? "-"}
            </span>
          )}
        </div>

        {voiceTagConfig === null ? (
          <div className="px-5 py-6 text-center">
            <p className="text-xs text-neutral-500 dark:text-neutral-400">
              {t("noVoiceTagHint")}
            </p>
          </div>
        ) : (
          <div className="flex flex-col gap-0">
            <div className="grid grid-cols-2 lg:grid-cols-4">
              <div className="flex flex-col gap-2 border-r border-b lg:border-b-0 border-neutral-100 dark:border-neutral-800/50 p-4">
                <span className="text-[11px] font-medium uppercase tracking-wider text-neutral-400 dark:text-neutral-500">
                  {tConfig("volumePercentage")}
                </span>
                <span className="text-xl font-bold tabular-nums text-black dark:text-white">
                  {voiceTagConfig.volumePercentage}%
                </span>
                <div className="h-1.5 w-full overflow-hidden rounded-full bg-neutral-100 dark:bg-neutral-800">
                  <div
                    className="h-full rounded-full bg-black dark:bg-white transition-all"
                    style={{ width: `${Math.min(voiceTagConfig.volumePercentage, 100)}%` }}
                  />
                </div>
              </div>

              <div className="flex flex-col gap-2 border-b lg:border-r lg:border-b-0 border-neutral-100 dark:border-neutral-800/50 p-4">
                <span className="text-[11px] font-medium uppercase tracking-wider text-neutral-400 dark:text-neutral-500">
                  {tConfig("duckingPercentage")}
                </span>
                <span className="text-xl font-bold tabular-nums text-black dark:text-white">
                  {voiceTagConfig.duckingPercentage}%
                </span>
                <div className="h-1.5 w-full overflow-hidden rounded-full bg-neutral-100 dark:bg-neutral-800">
                  <div
                    className="h-full rounded-full bg-black dark:bg-white transition-all"
                    style={{ width: `${Math.min(voiceTagConfig.duckingPercentage, 100)}%` }}
                  />
                </div>
              </div>

              <div className="flex flex-col gap-2 border-r border-neutral-100 dark:border-neutral-800/50 p-4">
                <span className="text-[11px] font-medium uppercase tracking-wider text-neutral-400 dark:text-neutral-500">
                  {tConfig("intervalSeconds")}
                </span>
                <span className="text-xl font-bold tabular-nums text-black dark:text-white">
                  {voiceTagConfig.intervalSeconds}<span className="text-sm font-medium text-neutral-400">s</span>
                </span>
                <div className="flex items-center gap-1">
                  {Array.from({ length: Math.min(Math.ceil(voiceTagConfig.intervalSeconds / 10), 12) }).map((_, i) => (
                    <div key={i} className="h-1.5 flex-1 rounded-full bg-black dark:bg-white" />
                  ))}
                  {Array.from({ length: Math.max(0, 12 - Math.ceil(voiceTagConfig.intervalSeconds / 10)) }).map((_, i) => (
                    <div key={`e-${i}`} className="h-1.5 flex-1 rounded-full bg-neutral-100 dark:bg-neutral-800" />
                  ))}
                </div>
              </div>

              <div className="flex flex-col gap-2 p-4">
                <span className="text-[11px] font-medium uppercase tracking-wider text-neutral-400 dark:text-neutral-500">
                  {tConfig("startOffsetSeconds")}
                </span>
                <span className="text-xl font-bold tabular-nums text-black dark:text-white">
                  {voiceTagConfig.startOffsetSeconds}<span className="text-sm font-medium text-neutral-400">s</span>
                </span>
                <div className="flex items-center gap-1">
                  {Array.from({ length: Math.min(Math.ceil(voiceTagConfig.startOffsetSeconds / 10), 12) }).map((_, i) => (
                    <div key={i} className="h-1.5 flex-1 rounded-full bg-black dark:bg-white" />
                  ))}
                  {Array.from({ length: Math.max(0, 12 - Math.ceil(voiceTagConfig.startOffsetSeconds / 10)) }).map((_, i) => (
                    <div key={`e-${i}`} className="h-1.5 flex-1 rounded-full bg-neutral-100 dark:bg-neutral-800" />
                  ))}
                </div>
              </div>
            </div>

            <div className="border-t border-neutral-100 dark:border-neutral-800/50 px-5 py-2.5">
              <p className="text-[11px] text-neutral-400 dark:text-neutral-500">{t("configLockedHint")}</p>
            </div>
          </div>
        )}
      </section>

      <SongDeleteDialog
        song={song}
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        onSuccess={() => router.push("/dashboard/songs")}
      />
    </div>
  );
}
