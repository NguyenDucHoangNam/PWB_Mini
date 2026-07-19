"use client";

import { useState } from "react";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { ProcessingStatusBadge } from "./processing-status-badge";
import type { Song } from "../types";

interface SongCardProps {
  song: Song;
  onDelete: (song: Song) => void;
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`;
}

export function SongCard({ song, onDelete }: SongCardProps) {
  const tActions = useTranslations("voice.actions");

  return (
    <div className="flex flex-col gap-3 rounded-xl border border-neutral-200 bg-white p-4 dark:border-neutral-800 dark:bg-black">
      <div className="flex items-start justify-between gap-2">
        <div className="flex flex-col gap-1 min-w-0">
          <h3 className="truncate text-base font-semibold text-black dark:text-white">
            {song.title}
          </h3>
          <div className="flex items-center gap-2 text-xs text-neutral-500 dark:text-neutral-400">
            <ProcessingStatusBadge status={song.status} />
            <span>{song.format.toUpperCase()}</span>
            <span>{formatBytes(song.fileSizeBytes)}</span>
          </div>
          {song.artist && (
            <p className="truncate text-xs text-neutral-500 dark:text-neutral-400">
              {song.artist}
            </p>
          )}
        </div>
        <div className="flex shrink-0 gap-2">
          <Link href={`/dashboard/songs/${song.id}`}>
            <Button variant="outline" size="sm">
              {tActions("edit")}
            </Button>
          </Link>
          <Button variant="destructive" size="sm" onClick={() => onDelete(song)}>
            {tActions("delete")}
          </Button>
        </div>
      </div>
    </div>
  );
}