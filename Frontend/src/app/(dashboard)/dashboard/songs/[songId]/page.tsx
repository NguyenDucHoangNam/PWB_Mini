"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { ArrowLeft, Loader2, Trash2, Pencil, Check, X, Music } from "lucide-react";
import { Spinner } from "@/components/ui/spinner";
import { PageHeader } from "@/components/layout/page-header";
import {
  NEU_INPUT,
  NEU_LABEL,
  NEU_TEXT,
  NEU_TEXT_MUTED,
  NeuBadge,
  NeuButton,
  NeuPanel,
  NeuScreen,
  neuButton,
} from "@/components/ui/neu";
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
    <NeuPanel tone="raised-sm" className="flex flex-col justify-center gap-2.5 rounded-2xl p-4">
      <div className="flex items-baseline justify-between gap-3">
        <dt className={NEU_LABEL}>{label}</dt>
        <dd className={`shrink-0 text-base font-bold tabular-nums ${NEU_TEXT}`}>{value}</dd>
      </div>
      {fillPercent !== undefined && (
        <div className="neu-pressed-sm h-2 w-full overflow-hidden rounded-full border-none">
          <div
            className="h-full rounded-full bg-indigo-600 dark:bg-indigo-400"
            style={{ width: `${Math.min(Math.max(fillPercent, 0), 100)}%` }}
          />
        </div>
      )}
    </NeuPanel>
  );
}

export default function SongDetailPage() {
  const params = useParams();
  const router = useRouter();
  const queryClient = useQueryClient();
  const songId = (params?.songId as string) ?? "";
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

  if (isLoading) {
    return (
      <NeuScreen>
        <NeuPanel
          tone="pressed"
          role="status"
          className="flex flex-1 items-center justify-center gap-3 py-16"
        >
          <Spinner size="md" />
          <span className="sr-only">{tList("loading")}</span>
        </NeuPanel>
      </NeuScreen>
    );
  }

  if (!songRes?.data) {
    return (
      <NeuScreen>
        <NeuPanel
          tone="pressed"
          role="alert"
          className="flex flex-1 flex-col items-center justify-center gap-5 py-16 text-center"
        >
          <p className={`text-sm font-semibold ${NEU_TEXT}`}>{tErrors("songNotFound")}</p>
          <NeuButton onClick={() => router.push("/dashboard/songs")}>{tActions("back")}</NeuButton>
        </NeuPanel>
      </NeuScreen>
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
    <NeuScreen>
      <PageHeader
        title={t("pageTitle")}
        icon={Music}
        actions={
          <Link href="/dashboard/songs" className={neuButton()}>
            <ArrowLeft className="size-4" aria-hidden="true" />
            {tActions("back")}
          </Link>
        }
      />

      {song.status === "PROCESSING" && (
        <NeuPanel
          tone="pressed"
          role="status"
          className={`flex items-center gap-3 rounded-2xl px-5 py-4 text-sm font-semibold ${NEU_TEXT}`}
        >
          <Spinner size="sm" />
          <span>{t("processingBanner")}</span>
        </NeuPanel>
      )}

      {song.status === "FAILED" && (
        <NeuPanel
          tone="pressed"
          role="alert"
          className="rounded-2xl p-5 text-sm text-rose-700 dark:text-rose-400"
        >
          <strong className="block font-bold">{t("failedTitle")}</strong>
          <p className="mt-1 break-words font-medium">
            {song.lastError || t("failedUnknownReason")}
          </p>
        </NeuPanel>
      )}

      {/* The two slabs split the leftover height 2:1, so the page fills the viewport instead
          of leaving dead space under a short card. */}
      <NeuPanel as="section" className="flex min-h-[15rem] flex-[3] flex-col gap-5 p-5 sm:p-6">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between sm:gap-4">
          <div className="flex min-w-0 flex-1 flex-col gap-2">
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
                  className={`${NEU_INPUT} h-12 min-w-0 flex-1 text-xl font-bold tracking-tight sm:text-2xl`}
                />
                <NeuButton
                  variant="primary"
                  size="icon-sm"
                  className="size-11 shrink-0 sm:size-10"
                  disabled={isSaving || !editTitle.trim()}
                  onMouseDown={(e) => {
                    e.preventDefault();
                    saveTitle();
                  }}
                  aria-label={tCommon("save")}
                >
                  <Check className="size-4" aria-hidden="true" />
                </NeuButton>
                <NeuButton
                  variant="ghost"
                  size="icon-sm"
                  className="size-11 shrink-0 sm:size-10"
                  onMouseDown={(e) => {
                    e.preventDefault();
                    cancelEditTitle();
                  }}
                  aria-label={tCommon("cancel")}
                >
                  <X className="size-4" aria-hidden="true" />
                </NeuButton>
              </div>
            ) : (
              <div className="flex min-w-0 items-center gap-2">
                <h1 className={`truncate text-xl font-bold tracking-tight sm:text-2xl ${NEU_TEXT}`}>
                  {song.title}
                </h1>
                <NeuButton
                  variant="ghost"
                  size="icon-sm"
                  className="shrink-0"
                  onClick={startEditTitle}
                  aria-label={tActions("edit")}
                >
                  <Pencil className="size-4" aria-hidden="true" />
                </NeuButton>
                <SongStatusBadge status={song.status} />
              </div>
            )}

            <div
              className={`flex flex-wrap items-center gap-x-2 gap-y-1 text-xs font-medium ${NEU_TEXT_MUTED}`}
            >
              <SongVoiceTagBadge hasVoiceTag={song.hasVoiceTag} />
              {metadata.map((entry, index) => (
                <span key={`${index}-${entry}`} className="flex items-center gap-2">
                  {index > 0 && <span aria-hidden="true">·</span>}
                  {entry}
                </span>
              ))}
            </div>
          </div>

          <div className="flex shrink-0 items-center gap-3">
            {song.status === "FAILED" && (
              <NeuButton
                variant="primary"
                disabled={isRetrying}
                onClick={() => retryProcessing({ songId: song.id })}
              >
                {isRetrying && <Loader2 className="size-4 animate-spin" aria-hidden="true" />}
                {t("retryProcessing")}
              </NeuButton>
            )}
            <NeuButton
              size="icon-sm"
              className="size-11 shrink-0 hover:text-rose-700 sm:size-10 dark:hover:text-rose-400"
              onClick={() => setDeleteOpen(true)}
              aria-label={tActions("delete")}
            >
              <Trash2 className="size-4" aria-hidden="true" />
            </NeuButton>
          </div>
        </div>

        <NeuPanel tone="pressed" className="flex flex-1 flex-col justify-center p-4 sm:p-6">
          <AudioPlayer songId={song.id} />
        </NeuPanel>
      </NeuPanel>

      <NeuPanel as="section" className="flex flex-1 flex-col gap-4 p-5 sm:p-6">
        <div className="flex items-center justify-between gap-3">
          <h2 className={`text-sm font-bold ${NEU_TEXT}`}>{tConfig("title")}</h2>
          {voiceTagConfig && (
            <NeuBadge tone="accent" className="max-w-[60%] truncate">
              {voiceTagConfig.voiceTagName ?? "—"}
            </NeuBadge>
          )}
        </div>

        {voiceTagConfig === null ? (
          <p className={`py-6 text-center text-sm font-medium ${NEU_TEXT_MUTED}`}>
            {t("noVoiceTagHint")}
          </p>
        ) : (
          <>
            {/* Separate raised tiles instead of hairline dividers — the style has no borders
                to draw them with, so the gap between slabs does the separating. */}
            <dl className="grid flex-1 grid-cols-2 gap-4 lg:grid-cols-4">
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
            </dl>

            <p className={`text-xs leading-relaxed ${NEU_TEXT_MUTED}`}>{t("configLockedHint")}</p>
          </>
        )}
      </NeuPanel>

      <SongDeleteDialog
        song={song}
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        onSuccess={() => router.push("/dashboard/songs")}
      />
    </NeuScreen>
  );
}
