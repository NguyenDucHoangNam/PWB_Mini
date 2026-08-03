"use client";

import { useState } from "react";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { Pencil, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { SongEditDialog } from "./song-edit-dialog";
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
  const [editOpen, setEditOpen] = useState(false);

  return (
    <Link
      href={`/dashboard/songs/${song.id}`}
      className="group relative flex flex-col justify-between gap-3.5 rounded-xl border border-neutral-200 bg-white p-4 shadow-sm transition-all duration-200 hover:border-neutral-300 hover:shadow-md dark:border-neutral-800 dark:bg-black dark:hover:border-neutral-700"
    >
      <div className="flex items-center justify-between gap-3">
        <div className="flex flex-col gap-1 min-w-0">
          <h3 className="truncate text-base font-bold tracking-tight text-neutral-900 dark:text-neutral-50">
            {song.title}
          </h3>
          <div className="flex items-center gap-2 text-xs text-neutral-500 dark:text-neutral-400">
            <span>{song.format.toUpperCase()}</span>
            <span>{formatBytes(song.fileSizeBytes)}</span>
          </div>
        </div>
        <div className="flex shrink-0 items-center gap-1">
          <Button
            variant="ghost"
            size="icon"
            className="size-8 text-neutral-500 hover:bg-neutral-100 hover:text-black dark:text-neutral-400 dark:hover:bg-neutral-800 dark:hover:text-white"
            onClick={(e) => {
              e.preventDefault();
              e.stopPropagation();
              setEditOpen(true);
            }}
            title={tActions("edit")}
          >
            <Pencil className="size-4" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            className="size-8 text-neutral-500 hover:bg-red-50 hover:text-red-600 dark:text-neutral-400 dark:hover:bg-red-950/40 dark:hover:text-red-400"
            onClick={(e) => {
              e.preventDefault();
              e.stopPropagation();
              onDelete(song);
            }}
            title={tActions("delete")}
          >
            <Trash2 className="size-4" />
          </Button>
        </div>
      </div>

      <SongEditDialog
        songId={song.id}
        currentTitle={song.title}
        open={editOpen}
        onOpenChange={setEditOpen}
      />
    </Link>
  );
}