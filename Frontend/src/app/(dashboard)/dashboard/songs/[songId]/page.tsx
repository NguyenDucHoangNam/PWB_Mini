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
import { formatBytes, formatDuration } from "@/features/voice/lib/format-audio";
import { resolveVoiceErrorMessage } from "@/features/voice/lib/resolve-voice-error-message";
import type { SongStatus } from "@/features/voice/types";

const POLL_INTERVAL_MS = 3000;

interface ConfigMetricProps {
  label: string;
  value: string;
  fillPercent?: number;
}

function ConfigMetric({ label, value, fillPercent }: ConfigMetricProps) {
  return (
    <div className="flex flex-col gap-2 bg-card p-4">
      <span className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
        {label}
      </span>
      <span className="text-lg font-semibold tabular-nums text-foreground">{value}</span>
      {fillPercent !== undefined && (
        <div className="h-1 w-full overflow-hidden rounded-full bg-border">
          <div
            className="h-full rounded-full bg-primary"
            style={{ width: `${Math.min(Math.max(fillPercent, 0), 100)}%` }}
          />
        </div>
      )}
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
  const tConfig = useTranslations("voice.config");
  const tActions = useTranslations("voice.actions");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("voice.errors");
  const tList = useTranslations("voice.list");
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

  if (!isPro) {
    return <ProUpgradePrompt />;
  }

  if (isLoading) {
    return (
      <div role="status" className="flex items-center justify-center gap-3 py-16">
        <Spinner size="md" />
        <span className="sr-only">{tList("loading")}</span>
      </div>
    );
  }

  if (!songRes?.data) {
    return (
      <div role="alert" className="flex flex-col items-center gap-4 py-16 text-center">
        <p className="text-sm text-foreground">{tErrors("songNotFound")}</p>
        <Button variant="outline" size="lg" onClick={() => router.push("/dashboard/songs")}>
          {tActions("back")}
        </Button>
      </div>
    );
  }

  const song = songRes.data;

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

  const metadata = [
    song.format ? song.format.toUpperCase() : null,
    song.fileSizeBytes !== null ? formatBytes(song.fileSizeBytes) : null,
    song.durationSeconds !== null ? formatDuration(song.durationSeconds) : null,
    new Date(song.createdAt).toLocaleDateString(),
  ].filter((entry): entry is string => entry !== null);

  return (
    <div className="mx-auto flex w-full max-w-4xl flex-col gap-5 sm:gap-6">
      <button
        type="button"
        onClick={() => router.push("/dashboard/songs")}
        className="flex items-center gap-1.5 self-start text-sm font-medium text-muted-foreground beat-16th transition-colors ease-hammer hover:text-foreground"
      >
        <ArrowLeft className="size-4" aria-hidden="true" />
        {tActions("back")}
      </button>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between sm:gap-4">
        <div className="flex min-w-0 flex-col gap-2">
          {isEditingTitle ? (
            <div className="flex items-center gap-2">
              <input
                ref={titleInputRef}
                type="text"
                value={editTitle}
                onChange={(e) => setEditTitle(e.target.value)}
                onKeyDown={handleTitleKeyDown}
                onBlur={cancelEditTitle}
                maxLength={200}
                disabled={isSaving}
                aria-label={tActions("edit")}
                className="h-11 min-w-0 flex-1 rounded-lg border border-input bg-background px-3 text-xl font-semibold tracking-tight text-foreground outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 sm:text-2xl"
              />
              <Button
                variant="secondary"
                size="icon"
                className="size-11 shrink-0 sm:size-9"
                disabled={isSaving || !editTitle.trim()}
                onMouseDown={(e) => {
                  e.preventDefault();
                  saveTitle();
                }}
                aria-label={tCommon("save")}
              >
                <Check className="size-4" aria-hidden="true" />
              </Button>
              <Button
                variant="ghost"
                size="icon"
                className="size-11 shrink-0 sm:size-9"
                onMouseDown={(e) => {
                  e.preventDefault();
                  cancelEditTitle();
                }}
                aria-label={tCommon("cancel")}
              >
                <X className="size-4" aria-hidden="true" />
              </Button>
            </div>
          ) : (
            <div className="flex min-w-0 items-center gap-2">
              <h1 className="truncate text-xl font-semibold tracking-tight text-foreground sm:text-2xl">
                {song.title}
              </h1>
              <Button
                variant="ghost"
                size="icon"
                className="size-9 shrink-0 text-muted-foreground hover:text-foreground sm:size-8"
                onClick={startEditTitle}
                aria-label={tActions("edit")}
              >
                <Pencil className="size-4 sm:size-3.5" aria-hidden="true" />
              </Button>
              <SongStatusBadge status={song.status} />
            </div>
          )}

          <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-muted-foreground">
            <SongVoiceTagBadge hasVoiceTag={song.hasVoiceTag} />
            {metadata.map((entry, index) => (
              <span key={`${index}-${entry}`} className="flex items-center gap-2">
                {index > 0 && <span aria-hidden="true">·</span>}
                {entry}
              </span>
            ))}
          </div>
        </div>

        <div className="flex shrink-0 items-center gap-2">
          {song.status === "FAILED" && (
            <Button
              size="lg"
              className="h-11 flex-1 sm:h-9 sm:flex-none"
              disabled={isRetrying}
              onClick={() => retryProcessing({ songId: song.id })}
            >
              {isRetrying && <Loader2 className="mr-2 size-3.5 animate-spin" aria-hidden="true" />}
              {t("retryProcessing")}
            </Button>
          )}
          <Button
            variant="outline"
            size="icon"
            className="size-11 shrink-0 text-muted-foreground hover:text-destructive sm:size-9"
            onClick={() => setDeleteOpen(true)}
            aria-label={tActions("delete")}
          >
            <Trash2 className="size-4" aria-hidden="true" />
          </Button>
        </div>
      </div>

      {song.status === "PROCESSING" && (
        <div
          role="status"
          className="flex items-center gap-3 rounded-xl border border-border bg-secondary px-4 py-3 text-sm text-secondary-foreground"
        >
          <Spinner size="sm" />
          <span>{t("processingBanner")}</span>
        </div>
      )}

      {song.status === "FAILED" && (
        <div
          role="alert"
          className="rounded-xl border border-destructive/30 bg-destructive/10 p-4 text-sm text-destructive"
        >
          <strong className="block font-semibold">{t("failedTitle")}</strong>
          <p className="mt-1 break-words">{song.lastError || t("failedUnknownReason")}</p>
        </div>
      )}

      <div className="rounded-xl border border-border bg-card p-4 sm:px-5 sm:py-5">
        <AudioPlayer songId={song.id} />
      </div>

      <section className="overflow-hidden rounded-xl border border-border bg-card">
        <div className="flex items-center justify-between gap-3 border-b border-border px-4 py-3 sm:px-5">
          <h2 className="text-sm font-semibold text-foreground">{tConfig("title")}</h2>
          {voiceTagConfig && (
            <span className="truncate rounded-md bg-secondary px-2.5 py-1 text-xs font-medium text-secondary-foreground">
              {voiceTagConfig.voiceTagName ?? "—"}
            </span>
          )}
        </div>

        {voiceTagConfig === null ? (
          <p className="px-4 py-6 text-center text-sm text-muted-foreground sm:px-5">
            {t("noVoiceTagHint")}
          </p>
        ) : (
          <>
            <div className="grid grid-cols-2 gap-px bg-border lg:grid-cols-4">
              <ConfigMetric
                label={tConfig("volumePercentage")}
                value={`${voiceTagConfig.volumePercentage}%`}
                fillPercent={voiceTagConfig.volumePercentage}
              />
              <ConfigMetric
                label={tConfig("duckingPercentage")}
                value={`${voiceTagConfig.duckingPercentage}%`}
                fillPercent={voiceTagConfig.duckingPercentage}
              />
              <ConfigMetric
                label={tConfig("intervalSeconds")}
                value={`${voiceTagConfig.intervalSeconds}s`}
              />
              <ConfigMetric
                label={tConfig("startOffsetSeconds")}
                value={`${voiceTagConfig.startOffsetSeconds}s`}
              />
            </div>

            <p className="border-t border-border px-4 py-3 text-xs text-muted-foreground sm:px-5">
              {t("configLockedHint")}
            </p>
          </>
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
