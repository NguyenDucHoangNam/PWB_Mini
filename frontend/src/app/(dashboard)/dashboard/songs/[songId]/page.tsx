"use client";

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { useProGuard } from "@/features/auth/hooks/use-pro-guard";
import { ProUpgradePrompt } from "@/features/voice/components/pro-upgrade-prompt";
import { SongDeleteDialog } from "@/features/voice/components/song-delete-dialog";
import { AudioPlayer } from "@/features/voice/components/audio-player";
import { useSong } from "@/features/voice/api/songs";

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`;
}

export default function SongDetailPage() {
  const params = useParams();
  const router = useRouter();
  const songId = (params?.songId as string) ?? "";
  const { isPro } = useProGuard();
  const tActions = useTranslations("voice.actions");
  const tVoiceErrors = useTranslations("voice.errors");
  const [deleteOpen, setDeleteOpen] = useState(false);

  const { data: songRes, isLoading } = useSong({ songId });

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
        <p className="text-sm text-red-600 dark:text-red-400">{tVoiceErrors("songNotFound")}</p>
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
        <div className="flex flex-col gap-1">
          <h1 className="text-2xl font-bold tracking-tight text-black dark:text-white">
            {song.title}
          </h1>
          <div className="flex flex-wrap items-center gap-2 text-xs text-neutral-500 dark:text-neutral-400">
            <span>{song.format.toUpperCase()}</span>
            <span>{formatBytes(song.fileSizeBytes)}</span>
            {song.durationSeconds !== null && (
              <span>
                {Math.floor(song.durationSeconds / 60)}:
                {String(Math.floor(song.durationSeconds % 60)).padStart(2, "0")}
              </span>
            )}
          </div>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => router.push("/dashboard/songs")}>
            {tActions("back")}
          </Button>
          <Button variant="destructive" onClick={() => setDeleteOpen(true)}>
            {tActions("delete")}
          </Button>
        </div>
      </div>

      <div className="rounded-xl border border-neutral-200 bg-white p-4 dark:border-neutral-800 dark:bg-black">
        <AudioPlayer songId={song.id} />
      </div>

      <SongDeleteDialog
        song={song}
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
      />
    </div>
  );
}