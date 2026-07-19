"use client";

import { useState } from "react";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { DEFAULT_PAGE_SIZE } from "@/lib/constants";
import { SongCard } from "@/features/voice/components/song-card";
import { SongDeleteDialog } from "@/features/voice/components/song-delete-dialog";
import { useListSongs } from "@/features/voice/api/songs";
import type { Song, SongStatus } from "@/features/voice/types";

type StatusFilter = "ALL" | SongStatus;

export function DashboardSongsTab() {
  const t = useTranslations("voice.songs");
  const tStatus = useTranslations("voice.status");
  const tActions = useTranslations("voice.actions");
  const tList = useTranslations("voice.list");

  const [page, setPage] = useState(0);
  const [filter, setFilter] = useState<StatusFilter>("ALL");
  const [toDelete, setToDelete] = useState<Song | null>(null);

  const { data, isLoading, isFetching, isError } = useListSongs({
    page,
    size: DEFAULT_PAGE_SIZE,
    status: filter === "ALL" ? undefined : filter,
  });

  const items = data?.success && data.data ? data.data.content : [];
  const totalPages = data?.success && data.data ? data.data.totalPages : 0;

  const filters: { value: StatusFilter; label: string }[] = [
    { value: "ALL", label: tList("all") },
    { value: "UPLOADED", label: tStatus("uploaded") },
    { value: "PROCESSING", label: tStatus("processing") },
    { value: "PROCESSED", label: tStatus("processed") },
    { value: "FAILED", label: tStatus("failed") },
  ];

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap gap-2">
        {filters.map((opt) => (
          <button
            key={opt.value}
            type="button"
            onClick={() => {
              setFilter(opt.value);
              setPage(0);
            }}
            aria-pressed={filter === opt.value}
            className={`rounded-full border px-3 py-1 text-xs font-semibold transition-colors ${
              filter === opt.value
                ? "border-black bg-black text-white dark:border-white dark:bg-white dark:text-black"
                : "border-neutral-200 bg-white text-neutral-700 hover:bg-neutral-50 dark:border-neutral-800 dark:bg-black dark:text-neutral-300 dark:hover:bg-neutral-900"
            }`}
          >
            {opt.label}
          </button>
        ))}
      </div>

      <div className="rounded-xl border border-neutral-200 bg-white dark:border-neutral-800 dark:bg-black">
        {isLoading || isFetching ? (
          <div className="flex items-center justify-center gap-3 p-12 text-sm text-neutral-500">
            <Spinner size="md" />
            {tList("loading")}
          </div>
        ) : isError ? (
          <div role="alert" className="p-12 text-center text-sm text-red-600 dark:text-red-400">
            {tList("loading")}
          </div>
        ) : items.length === 0 ? (
          <div className="flex flex-col items-center gap-3 p-12 text-center">
            <h2 className="text-lg font-semibold text-black dark:text-white">{t("noSongs")}</h2>
            <Link href="/dashboard/songs/new">
              <Button className="mt-2">{t("upload")}</Button>
            </Link>
          </div>
        ) : (
          <div className="grid gap-3 p-4 sm:grid-cols-2 xl:grid-cols-3">
            {items.map((song) => (
              <SongCard key={song.id} song={song} onDelete={setToDelete} />
            ))}
          </div>
        )}
      </div>

      {totalPages > 1 && (
        <div className="flex items-center justify-end gap-2">
          <Button
            variant="outline"
            size="sm"
            disabled={page === 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            {tList("prev")}
          </Button>
          <span className="text-xs text-neutral-500 dark:text-neutral-400">
            {page + 1} / {totalPages}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={page + 1 >= totalPages}
            onClick={() => setPage((p) => p + 1)}
          >
            {tList("next")}
          </Button>
        </div>
      )}

      <SongDeleteDialog
        song={toDelete}
        open={toDelete !== null}
        onOpenChange={(o) => !o && setToDelete(null)}
      />
    </div>
  );
}