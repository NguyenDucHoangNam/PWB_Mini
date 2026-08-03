"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { asApiError } from "@/lib/api-client";
import {
  useSong,
  useAudioUrl,
  useTriggerProcessing,
} from "@/features/audio";
import type { SongStatus } from "@/features/audio/types";

const POLL_INTERVAL_MS = 3000;
const POLL_TIMEOUT_MS = 120000;

function formatDuration(seconds: number | null) {
  if (seconds === null || Number.isNaN(seconds)) return "-";
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${m}:${String(s).padStart(2, "0")}`;
}

function StatusBadge({ status }: { status: SongStatus }) {
  const styles: Record<SongStatus, string> = {
    PROCESSING: "border-yellow-300 bg-yellow-50 text-yellow-800 dark:border-yellow-800 dark:bg-yellow-950/30 dark:text-yellow-300",
    UPLOADED: "border-blue-300 bg-blue-50 text-blue-800 dark:border-blue-800 dark:bg-blue-950/30 dark:text-blue-300",
    PROCESSED: "border-green-300 bg-green-50 text-green-800 dark:border-green-800 dark:bg-green-950/30 dark:text-green-300",
    FAILED: "border-red-300 bg-red-50 text-red-800 dark:border-red-800 dark:bg-red-950/30 dark:text-red-300",
  };
  return (
    <span className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold ${styles[status]}`}>
      {status}
    </span>
  );
}

export default function SongDetailPage() {
  const params = useParams<{ songId: string }>();
  const router = useRouter();
  const t = useTranslations("dashboard.songDetail");

  const songId = params?.songId ?? "";

  const { data, isLoading, isError, refetch } = useSong({ songId });
  const { data: audioData, refetch: refetchAudio } = useAudioUrl({
    songId,
    variant: "PROCESSED",
  });

  const pollingStartedAt = useRef<number | null>(null);
  const audioRef = useRef<HTMLAudioElement>(null);

  useEffect(() => {
    const status = data?.success ? data.data?.status : null;
    if (status !== "PROCESSING") {
      pollingStartedAt.current = null;
      return;
    }
    if (pollingStartedAt.current === null) {
      pollingStartedAt.current = Date.now();
    }
    const elapsed = Date.now() - (pollingStartedAt.current ?? Date.now());
    if (elapsed >= POLL_TIMEOUT_MS) {
      pollingStartedAt.current = null;
      return;
    }
    const id = setTimeout(() => {
      refetch();
    }, POLL_INTERVAL_MS);
    return () => clearTimeout(id);
  }, [data, refetch]);

  const { mutate: triggerMutate, isPending: triggering } = useTriggerProcessing();

  const handleTriggerProcessing = () => {
    triggerMutate(
      { songId },
      {
        onSuccess: (res) => {
          if (res.success) {
            toast.success(t("toastProcessingTriggered"));
            refetch();
          }
        },
        onError: asApiError((err) => toast.error(err.message)),
      },
    );
  };

  const handlePlay = () => {
    if (audioData?.data?.url) {
      refetchAudio();
    }
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center gap-3 p-12 text-sm text-neutral-500">
        <Spinner size="md" />
        {t("loading")}
      </div>
    );
  }

  if (isError || !data?.success || !data.data) {
    return (
      <div className="flex flex-col items-center gap-4 p-12 text-center">
        <p className="text-sm text-neutral-500">{t("notFound")}</p>
        <Button variant="outline" onClick={() => router.push("/songs")}>
          {t("back")}
        </Button>
      </div>
    );
  }

  const song = data.data;
  const audioUrl = audioData?.data?.url ?? null;

  return (
    <div className="flex flex-col gap-6 font-sans">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-3">
          <Link href="/songs" className="text-sm text-neutral-500 underline-offset-4 hover:underline">
            &larr; {t("back")}
          </Link>
        </div>
        <div className="flex items-center gap-3">
          <StatusBadge status={song.status} />
          {song.status === "UPLOADED" && (
            <Button variant="default" size="sm" onClick={handleTriggerProcessing} disabled={triggering}>
              {triggering ? <Spinner size="sm" /> : t("triggerProcessing")}
            </Button>
          )}
          {song.status === "FAILED" && (
            <Button variant="default" size="sm" onClick={handleTriggerProcessing} disabled={triggering}>
              {triggering ? <Spinner size="sm" /> : t("retryProcessing")}
            </Button>
          )}
        </div>
      </div>

      <header className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold tracking-tight text-black dark:text-white">
          {song.title}
        </h1>
        {song.artist && (
          <p className="text-sm text-neutral-500 dark:text-neutral-400">{song.artist}</p>
        )}
      </header>

      {song.status === "PROCESSING" && (
        <div className="flex items-center gap-3 rounded-lg border border-yellow-300 bg-yellow-50 px-4 py-3 text-sm text-yellow-800 dark:border-yellow-800 dark:bg-yellow-950/30 dark:text-yellow-300">
          <Spinner size="sm" />
          <span>{t("processingInProgress")}</span>
        </div>
      )}

      {song.status === "FAILED" && song.lastError && (
        <div className="rounded-lg border border-red-300 bg-red-50 p-4 text-sm text-red-800 dark:border-red-800 dark:bg-red-950/30 dark:text-red-300">
          <strong className="block">{t("metadataErrorTitle")}</strong>
          <p className="mt-1 break-words">{song.lastError}</p>
        </div>
      )}

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="flex flex-col gap-4 lg:col-span-2">
          <div className="rounded-xl border border-neutral-200 bg-white p-5 dark:border-neutral-800 dark:bg-black">
            <h2 className="mb-3 text-sm font-semibold text-neutral-700 dark:text-neutral-300">
              {song.title}
            </h2>
            {song.status === "PROCESSED" ? (
              <>
                <audio
                  ref={audioRef}
                  controls
                  className="mt-4 w-full"
                  src={audioUrl ?? undefined}
                  preload="metadata"
                >
                  {t("playerLabel")}
                </audio>
                {!audioUrl && (
                  <Button size="sm" variant="outline" onClick={handlePlay} className="mt-2">
                    {t("loadAudio")}
                  </Button>
                )}
              </>
            ) : song.status === "PROCESSING" ? (
              <div className="mt-4 flex h-10 items-center justify-center rounded-lg border border-dashed border-neutral-300 bg-neutral-50 text-xs text-neutral-500 dark:border-neutral-700 dark:bg-neutral-900/40">
                <Spinner size="sm" className="mr-2" />
                {t("processingInProgress")}
              </div>
            ) : (
              <div className="mt-4 flex h-10 items-center justify-center rounded-lg border border-dashed border-neutral-300 bg-neutral-50 text-xs text-neutral-500 dark:border-neutral-700 dark:bg-neutral-900/40">
                {t("playerNotAvailable")}
              </div>
            )}
          </div>
        </div>

        <aside className="flex flex-col gap-3 rounded-xl border border-neutral-200 bg-white p-5 text-sm dark:border-neutral-800 dark:bg-black">
          <Row label={t("metadataFormat")} value={(song.format ?? "-").toUpperCase()} />
          <Row label={t("metadataDuration")} value={formatDuration(song.durationSeconds)} />
          <Row label={t("metadataFileSize")} value={formatBytes(song.fileSizeBytes)} />
          {song.album && <Row label={t("metadataAlbum")} value={song.album} />}
          <Row label={t("metadataCreatedAt")} value={formatDate(song.createdAt)} />
        </aside>
      </div>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-3 py-1.5">
      <span className="text-xs font-medium text-neutral-500 dark:text-neutral-400">{label}</span>
      <span className="text-sm font-semibold text-black dark:text-white">{value}</span>
    </div>
  );
}

function formatBytes(bytes: number | null) {
  if (bytes === null) return "-";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`;
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleString();
}
