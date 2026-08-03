"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { DEFAULT_PAGE_SIZE } from "@/lib/constants";
import { useSongs, useDistributions } from "@/features/audio";
import type { SongListItem } from "@/features/audio/types";

interface SummaryRow {
  song: SongListItem;
}

function formatBytes(bytes: number | null) {
  if (bytes === null) return "-";
  if (bytes < 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`;
}

export default function DistributionsPage() {
  const t = useTranslations("dashboard.distributions");

  const { data: songsRes, isLoading: songsLoading } = useSongs({
    page: 0,
    size: 100,
  });
  const songs: SongListItem[] =
    songsRes?.success && songsRes.data ? songsRes.data.content : [];

  return (
    <div className="flex flex-col gap-6 font-sans">
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold tracking-tight text-black dark:text-white">
          {t("title")}
        </h1>
        <p className="text-sm text-neutral-500 dark:text-neutral-400">{t("subtitle")}</p>
      </div>

      {songsLoading ? (
        <div className="flex items-center justify-center gap-3 p-12 text-sm text-neutral-500">
          <Spinner size="md" />
          {t("loadingTop")}
        </div>
      ) : songs.length === 0 ? (
        <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed border-neutral-300 p-12 text-center dark:border-neutral-700">
          <h2 className="text-lg font-semibold text-black dark:text-white">{t("emptyTitle")}</h2>
          <p className="max-w-md text-sm text-neutral-500 dark:text-neutral-400">{t("emptyDesc")}</p>
        </div>
      ) : (
        <div className="rounded-xl border border-neutral-200 bg-white dark:border-neutral-800 dark:bg-black">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-neutral-200 bg-neutral-50 text-left text-xs uppercase tracking-wide text-neutral-500 dark:border-neutral-800 dark:bg-neutral-900/40">
                  <th className="px-4 py-3 font-medium">{t("colSong")}</th>
                  <th className="px-4 py-3 font-medium">{t("colTotal")}</th>
                  <th className="px-4 py-3 font-medium text-right">{t("colActions")}</th>
                </tr>
              </thead>
              <tbody>
                {songs.map((song) => (
                  <tr
                    key={song.id}
                    className="border-b border-neutral-200 last:border-b-0 hover:bg-neutral-50/60 dark:border-neutral-800 dark:hover:bg-neutral-900/30"
                  >
                    <td className="px-4 py-3">
                      <div className="font-medium text-black dark:text-white">{song.title}</div>
                      {song.artist && (
                        <div className="text-xs text-neutral-500">{song.artist}</div>
                      )}
                    </td>
                    <td className="px-4 py-3 tabular-nums">
                      <DistributionSummaryCell songId={song.id} />
                    </td>
                    <td className="px-4 py-3 text-right">
                      <Link href={`/distributions/${song.id}`}>
                        <Button size="sm" variant="outline">
                          {t("viewDetail")}
                        </Button>
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}

function DistributionSummaryCell({ songId }: { songId: string }) {
  const { data, isLoading } = useDistributions({
    songId,
    page: 0,
    size: 1,
    includeRevoked: true,
  });
  if (isLoading) {
    return <Spinner size="sm" />;
  }
  if (!data?.success || !data.data) {
    return <span className="text-neutral-500">-</span>;
  }
  return <span>{data.data.totalElements}</span>;
}
