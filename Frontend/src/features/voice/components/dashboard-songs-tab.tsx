"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { Music, Upload, AlertCircle, SearchX } from "lucide-react";
import { Button } from "@/components/ui/button";
import { SearchInput } from "@/components/ui/search-input";
import { Spinner } from "@/components/ui/spinner";
import { useDebouncedValue } from "@/hooks/use-debounced-value";
import { DEFAULT_PAGE_SIZE } from "@/lib/constants";
import { SongCard } from "@/features/voice/components/song-card";
import { SongDeleteDialog } from "@/features/voice/components/song-delete-dialog";
import { useListSongs, useSearchSongs } from "@/features/voice/api/songs";
import { SONG_VIEW_STATUSES } from "@/features/voice/types";
import type { Song, SongView } from "@/features/voice/types";

const LIST_POLL_INTERVAL_MS = 5000;
const SEARCH_DEBOUNCE_MS = 250;

const SONG_VIEWS = Object.keys(SONG_VIEW_STATUSES) as SongView[];

function parseView(value: string | null): SongView {
  return SONG_VIEWS.includes(value as SongView) ? (value as SongView) : "ALL";
}

function parsePage(value: string | null): number {
  const parsed = Number(value ?? "0");
  return Number.isFinite(parsed) && parsed >= 0 ? Math.floor(parsed) : 0;
}

export function DashboardSongsTab() {
  const t = useTranslations("voice.songs");
  const tStatus = useTranslations("voice.status");
  const tList = useTranslations("voice.list");

  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const view = useMemo(() => parseView(searchParams.get("view")), [searchParams]);
  const page = useMemo(() => parsePage(searchParams.get("page")), [searchParams]);
  const queryFromUrl = searchParams.get("q") ?? "";

  // The field is local so typing stays instant; the URL only catches up once the debounced value does,
  // which also keeps a search shareable and survivable across a reload.
  const [keyword, setKeyword] = useState(queryFromUrl);
  const debouncedKeyword = useDebouncedValue(keyword, SEARCH_DEBOUNCE_MS);
  const searching = debouncedKeyword.trim().length > 0;

  const buildQuery = (next: Record<string, string | null>) => {
    const params = new URLSearchParams(searchParams.toString());
    for (const [key, value] of Object.entries(next)) {
      if (value === null || value === "") params.delete(key);
      else params.set(key, value);
    }
    const query = params.toString();
    return query ? `${pathname}?${query}` : pathname;
  };

  const updateQuery = (next: Record<string, string | null>) => {
    router.push(buildQuery(next));
  };

  const buildPageHref = useCallback(
    (nextPage: number) => {
      const params = new URLSearchParams(searchParams.toString());
      if (nextPage <= 0) params.delete("page");
      else params.set("page", String(nextPage));
      const query = params.toString();
      return query ? `${pathname}?${query}` : pathname;
    },
    [pathname, searchParams],
  );

  const listQuery = useListSongs({
    page,
    size: DEFAULT_PAGE_SIZE,
    status: SONG_VIEW_STATUSES[view],
    queryConfig: {
      enabled: !searching,
      // Songs land here straight from upload while still rendering; without this their badge would sit
      // on PROCESSING until the user reloaded by hand. The background flag matters because react-query
      // freezes interval refetches on a hidden tab, and waiting out a render is exactly when people
      // switch away.
      refetchIntervalInBackground: true,
      refetchInterval: (query) =>
        query.state.data?.data?.content.some((song) => song.status === "PROCESSING")
          ? LIST_POLL_INTERVAL_MS
          : false,
    },
  });

  const searchQuery = useSearchSongs({
    page,
    size: DEFAULT_PAGE_SIZE,
    q: debouncedKeyword,
    status: SONG_VIEW_STATUSES[view],
    queryConfig: { enabled: searching },
  });

  const { data, isLoading, isFetching, isError, refetch } = searching ? searchQuery : listQuery;

  // The server applies both the status filter and the keyword, so this page and the page count already
  // describe the filtered set. Filtering here as well would only re-hide rows the query never returned.
  const pageData = data?.success && data.data ? data.data : null;
  const items = pageData ? pageData.content : [];
  const totalPages = pageData ? pageData.totalPages : 0;
  const toDeleteId = searchParams.get("delete");

  /**
   * Deleting the last song on the last page leaves the URL pointing past the end, where the list renders
   * the "no songs yet" state and the pager hides itself — nothing is left to click back with. The
   * response echoes the page it describes, so a stale keepPreviousData payload cannot trigger this.
   */
  useEffect(() => {
    if (!pageData || pageData.page !== page || page === 0 || page < totalPages) return;
    router.replace(buildPageHref(totalPages - 1));
  }, [pageData, page, totalPages, router, buildPageHref]);

  // Three groups, not four job states: a plain upload and a finished merge are both simply playable.
  const filters: { value: SongView; label: string }[] = [
    { value: "ALL", label: tList("all") },
    { value: "READY", label: tStatus("ready") },
    { value: "PROCESSING", label: tStatus("processing") },
    { value: "FAILED", label: tStatus("failed") },
  ];

  const setView = (value: SongView) => {
    updateQuery({ view: value === "ALL" ? null : value, page: null });
  };

  /**
   * Any change to the search text invalidates the page number: page 3 of the old result set has nothing
   * to do with page 3 of the new one.
   *
   * `replace`, not `push`: typing one word would otherwise leave a history entry per pause, so Back
   * would walk letter by letter back out of the search instead of leaving the page.
   */
  useEffect(() => {
    if (debouncedKeyword === queryFromUrl) return;
    router.replace(buildQuery({ q: debouncedKeyword || null, page: null }));
    // buildQuery closes over the current params on purpose; re-running on its identity would loop.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedKeyword, queryFromUrl]);

  const setPage = (newPage: number) => {
    router.push(buildPageHref(newPage));
  };

  const openDelete = (song: Song) => {
    updateQuery({ delete: song.id });
  };

  const closeDelete = () => {
    updateQuery({ delete: null });
  };

  return (
    <div className="flex flex-col gap-4">
      <SearchInput
        value={keyword}
        onValueChange={setKeyword}
        loading={searching && isFetching}
        placeholder={tList("searchSongsPlaceholder")}
        clearLabel={tList("clearSearch")}
        aria-label={tList("searchSongsPlaceholder")}
      />

      <div className="flex flex-wrap gap-2">
        {filters.map((opt) => (
          <button
            key={opt.value}
            type="button"
            onClick={() => setView(opt.value)}
            aria-pressed={view === opt.value}
            className={`rounded-full border px-3 py-1 text-xs font-semibold transition-colors ${
              view === opt.value
                ? "border-black bg-black text-white dark:border-white dark:bg-white dark:text-black"
                : "border-neutral-200 bg-white text-neutral-700 hover:bg-neutral-50 dark:border-neutral-800 dark:bg-black dark:text-neutral-300 dark:hover:bg-neutral-900"
            }`}
          >
            {opt.label}
          </button>
        ))}
      </div>

      <div
        aria-busy={isFetching}
        className={`rounded-xl border border-neutral-200 bg-white transition-opacity dark:border-neutral-800 dark:bg-black ${
          isFetching && !isLoading ? "opacity-60" : ""
        }`}
      >
        {isLoading ? (
          <div className="flex items-center justify-center gap-3 p-12 text-sm text-neutral-500">
            <Spinner size="md" />
            {tList("loading")}
          </div>
        ) : isError ? (
          <div role="alert" className="flex flex-col items-center justify-center gap-3 p-12 text-center">
            <AlertCircle className="h-8 w-8 text-red-500" />
            <p className="text-sm font-medium text-red-600 dark:text-red-400">
              {t("errorLoad")}
            </p>
            <Button variant="outline" size="sm" onClick={() => refetch()}>
              {t("retry")}
            </Button>
          </div>
        ) : items.length === 0 && searching ? (
          // A library that has songs but none matching is a different situation from an empty library:
          // offering "upload your first song" here would be answering a question nobody asked.
          <div className="flex flex-col items-center justify-center gap-4 p-12 text-center">
            <div className="flex h-16 w-16 items-center justify-center rounded-full bg-neutral-100 dark:bg-neutral-900">
              <SearchX className="h-8 w-8 text-neutral-400" />
            </div>
            <div className="max-w-sm space-y-1">
              <h3 className="text-base font-semibold text-neutral-900 dark:text-neutral-100">
                {tList("noResults")}
              </h3>
              <p className="text-xs text-neutral-500 dark:text-neutral-400">
                {tList("noResultsHint", { query: debouncedKeyword })}
              </p>
            </div>
            <Button variant="outline" size="sm" onClick={() => setKeyword("")}>
              {tList("clearSearch")}
            </Button>
          </div>
        ) : items.length === 0 ? (
          <div className="flex flex-col items-center justify-center gap-4 p-12 text-center">
            <div className="flex h-16 w-16 items-center justify-center rounded-full bg-neutral-100 dark:bg-neutral-900">
              <Music className="h-8 w-8 text-neutral-400" />
            </div>
            <div className="max-w-sm space-y-1">
              <h3 className="text-base font-semibold text-neutral-900 dark:text-neutral-100">
                {t("noSongs")}
              </h3>
              <p className="text-xs text-neutral-500 dark:text-neutral-400">
                {t("emptyHint")}
              </p>
            </div>
            <Link href="/dashboard/songs/new">
              <Button className="mt-2 flex items-center gap-2">
                <Upload className="h-4 w-4" />
                {t("upload")}
              </Button>
            </Link>
          </div>
        ) : (
          <div className="grid gap-3 p-4 sm:grid-cols-2 xl:grid-cols-3">
            {items.map((song) => (
              <SongCard key={song.id} song={song} onDelete={openDelete} />
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
            onClick={() => setPage(Math.max(0, page - 1))}
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
            onClick={() => setPage(page + 1)}
          >
            {tList("next")}
          </Button>
        </div>
      )}

      <SongDeleteDialog
        song={items.find((song) => song.id === toDeleteId) ?? null}
        open={toDeleteId !== null}
        onOpenChange={(o) => !o && closeDelete()}
      />
    </div>
  );
}
