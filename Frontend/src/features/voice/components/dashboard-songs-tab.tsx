"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Music, Upload, AlertCircle, SearchX, Pencil, Trash2, Check, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { SearchInput } from "@/components/ui/search-input";
import { Spinner } from "@/components/ui/spinner";
import { useDebouncedValue } from "@/hooks/use-debounced-value";
import { DEFAULT_PAGE_SIZE } from "@/lib/constants";
import { asApiError } from "@/lib/api-client";
import { SongDeleteDialog } from "@/features/voice/components/song-delete-dialog";
import { SongStatusBadge, SongVoiceTagBadge } from "@/features/voice/components/song-status-badge";
import { useListSongs, useSearchSongs, useUpdateSong } from "@/features/voice/api/songs";
import { resolveVoiceErrorMessage } from "@/features/voice/lib/resolve-voice-error-message";
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

interface SongRowProps {
  song: Song;
  index: number;
  onDelete: (song: Song) => void;
}

function SongRow({ song, index, onDelete }: SongRowProps) {
  const router = useRouter();
  const tActions = useTranslations("voice.actions");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("voice.errors");

  const [isEditing, setIsEditing] = useState(false);
  const [editValue, setEditValue] = useState(song.title);
  const inputRef = useRef<HTMLInputElement>(null);
  const isOdd = index % 2 === 0;

  const { mutate: updateSong, isPending } = useUpdateSong({
    mutationConfig: {
      onSuccess: (response) => {
        if (response.success) {
          toast.success(tCommon("save"));
          setIsEditing(false);
        } else {
          toast.error(response.message || tCommon("error"));
        }
      },
      onError: asApiError((err) => {
        toast.error(resolveVoiceErrorMessage(err, tErrors, tCommon));
      }),
    },
  });

  useEffect(() => {
    if (isEditing && inputRef.current) {
      inputRef.current.focus();
      inputRef.current.select();
    }
  }, [isEditing]);

  const startEdit = () => {
    setEditValue(song.title);
    setIsEditing(true);
  };

  const cancelEdit = () => {
    setEditValue(song.title);
    setIsEditing(false);
  };

  const saveEdit = () => {
    const trimmed = editValue.trim();
    if (!trimmed || isPending) return;
    if (trimmed === song.title) {
      setIsEditing(false);
      return;
    }
    updateSong({ songId: song.id, data: { title: trimmed } });
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter") {
      e.preventDefault();
      saveEdit();
    } else if (e.key === "Escape") {
      cancelEdit();
    }
  };

  const handleRowClick = () => {
    if (!isEditing) {
      router.push(`/dashboard/songs/${song.id}`);
    }
  };

  return (
    <div
      role="link"
      tabIndex={0}
      onClick={handleRowClick}
      onKeyDown={(e) => {
        if (e.key === "Enter" && !isEditing) handleRowClick();
      }}
      className={`group relative flex items-center gap-3 px-4 py-3 transition-colors cursor-pointer hover:bg-neutral-100 dark:hover:bg-neutral-900 ${
        isOdd
          ? "bg-white dark:bg-black"
          : "bg-neutral-50/60 dark:bg-neutral-950/60"
      }`}
    >
      <div
        className={`absolute left-0 top-0 h-full w-[3px] transition-colors ${
          isOdd
            ? "bg-neutral-900 dark:bg-neutral-100"
            : "bg-transparent"
        }`}
        aria-hidden="true"
      />

      <span className="w-8 shrink-0 text-center text-xs font-mono text-neutral-400 dark:text-neutral-500">
        {index + 1}
      </span>

      <div className="flex flex-1 items-center gap-3 min-w-0">
        <div className="flex flex-col gap-0.5 min-w-0 flex-1">
          <div className="flex items-center gap-2 min-w-0">
            {isEditing ? (
              <div className="flex items-center gap-1.5 min-w-0 flex-1" onClick={(e) => e.stopPropagation()}>
                <input
                  ref={inputRef}
                  type="text"
                  value={editValue}
                  onChange={(e) => setEditValue(e.target.value)}
                  onKeyDown={handleKeyDown}
                  onBlur={cancelEdit}
                  maxLength={200}
                  disabled={isPending}
                  className="min-w-0 flex-1 rounded border border-neutral-300 bg-white px-2 py-0.5 text-sm font-semibold text-neutral-900 outline-none focus:border-black dark:border-neutral-700 dark:bg-neutral-900 dark:text-neutral-50 dark:focus:border-white"
                />
                <button
                  type="button"
                  onMouseDown={(e) => {
                    e.preventDefault();
                    saveEdit();
                  }}
                  disabled={isPending || !editValue.trim()}
                  className="flex size-6 shrink-0 items-center justify-center rounded text-neutral-600 hover:bg-neutral-200 dark:text-neutral-400 dark:hover:bg-neutral-800"
                  aria-label={tCommon("save")}
                >
                  <Check className="size-3.5" />
                </button>
                <button
                  type="button"
                  onMouseDown={(e) => {
                    e.preventDefault();
                    cancelEdit();
                  }}
                  className="flex size-6 shrink-0 items-center justify-center rounded text-neutral-600 hover:bg-neutral-200 dark:text-neutral-400 dark:hover:bg-neutral-800"
                  aria-label={tCommon("cancel")}
                >
                  <X className="size-3.5" />
                </button>
              </div>
            ) : (
              <>
                <span className="truncate text-sm font-semibold text-neutral-900 dark:text-neutral-50">
                  {song.title}
                </span>
                <SongStatusBadge status={song.status} />
              </>
            )}
          </div>
          <div className="flex items-center gap-2 sm:hidden">
            <SongVoiceTagBadge hasVoiceTag={song.hasVoiceTag} />
          </div>
        </div>
      </div>

      <div className="hidden sm:flex items-center">
        <SongVoiceTagBadge hasVoiceTag={song.hasVoiceTag} />
      </div>

      <span className="hidden md:block w-14 shrink-0 text-center text-xs font-medium text-neutral-500 dark:text-neutral-400 uppercase">
        {(song.format ?? "").toUpperCase()}
      </span>

      <span className="hidden lg:block w-16 shrink-0 text-right text-xs text-neutral-500 dark:text-neutral-400">
        {song.fileSizeBytes !== null ? formatBytes(song.fileSizeBytes) : "-"}
      </span>

      <span className="w-12 shrink-0 text-right text-xs font-mono text-neutral-600 dark:text-neutral-300">
        {song.durationSeconds !== null ? formatDuration(song.durationSeconds) : "-"}
      </span>

      <div className="flex shrink-0 items-center gap-0.5 opacity-0 group-hover:opacity-100 transition-opacity sm:w-16 justify-end">
        <Button
          variant="ghost"
          size="icon"
          className="size-7 text-neutral-500 hover:bg-neutral-200 hover:text-black dark:text-neutral-400 dark:hover:bg-neutral-800 dark:hover:text-white"
          onClick={(e) => {
            e.stopPropagation();
            startEdit();
          }}
          aria-label={tActions("edit")}
        >
          <Pencil className="size-3.5" />
        </Button>
        <Button
          variant="ghost"
          size="icon"
          className="size-7 text-neutral-500 hover:bg-red-50 hover:text-red-600 dark:text-neutral-400 dark:hover:bg-red-950/40 dark:hover:text-red-400"
          onClick={(e) => {
            e.stopPropagation();
            onDelete(song);
          }}
          aria-label={tActions("delete")}
        >
          <Trash2 className="size-3.5" />
        </Button>
      </div>
    </div>
  );
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

  const pageData = data?.success && data.data ? data.data : null;
  const items = pageData ? pageData.content : [];
  const totalPages = pageData ? pageData.totalPages : 0;
  const totalElements = pageData ? pageData.totalElements : 0;
  const toDeleteId = searchParams.get("delete");

  useEffect(() => {
    if (!pageData || pageData.page !== page || page === 0 || page < totalPages) return;
    router.replace(buildPageHref(totalPages - 1));
  }, [pageData, page, totalPages, router, buildPageHref]);

  const filters: { value: SongView; label: string }[] = [
    { value: "ALL", label: tList("all") },
    { value: "READY", label: tStatus("ready") },
    { value: "PROCESSING", label: tStatus("processing") },
    { value: "FAILED", label: tStatus("failed") },
  ];

  const setView = (value: SongView) => {
    updateQuery({ view: value === "ALL" ? null : value, page: null });
  };

  useEffect(() => {
    if (debouncedKeyword === queryFromUrl) return;
    router.replace(buildQuery({ q: debouncedKeyword || null, page: null }));
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

  const rangeFrom = page * DEFAULT_PAGE_SIZE + 1;
  const rangeTo = Math.min(rangeFrom + items.length - 1, totalElements);

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:gap-4">
        <div className="flex-1">
          <SearchInput
            value={keyword}
            onValueChange={setKeyword}
            loading={searching && isFetching}
            placeholder={tList("searchSongsPlaceholder")}
            clearLabel={tList("clearSearch")}
            aria-label={tList("searchSongsPlaceholder")}
          />
        </div>
        <select
          value={view}
          onChange={(e) => setView(e.target.value as SongView)}
          className="h-9 shrink-0 appearance-none rounded-lg border border-neutral-200 bg-white px-3 pr-8 text-xs font-semibold text-neutral-700 outline-none transition-colors hover:border-neutral-300 focus-visible:ring-2 focus-visible:ring-ring/50 dark:border-neutral-800 dark:bg-black dark:text-neutral-300 dark:hover:border-neutral-700"
          style={{ backgroundImage: `url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%23999' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='m6 9 6 6 6-6'/%3E%3C/svg%3E")`, backgroundRepeat: "no-repeat", backgroundPosition: "right 8px center" }}
        >
          {filters.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>
      </div>

      <div
        aria-busy={isFetching}
        className={`overflow-hidden rounded-xl border border-neutral-200 bg-white transition-opacity dark:border-neutral-800 dark:bg-black ${
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
          <>
            <div className="hidden sm:flex items-center gap-3 border-b border-neutral-100 px-4 py-2.5 text-[11px] font-semibold uppercase tracking-wider text-neutral-400 dark:border-neutral-800 dark:text-neutral-500">
              <span className="w-8 shrink-0 text-center">#</span>
              <span className="flex-1">{tList("colTitle")}</span>
              <span className="w-20" />
              <span className="hidden md:block w-14 shrink-0 text-center">{tList("colFormat")}</span>
              <span className="hidden lg:block w-16 shrink-0 text-right">{tList("colSize")}</span>
              <span className="w-12 shrink-0 text-right">{tList("colDuration")}</span>
              <span className="w-16 shrink-0" />
            </div>
            <div className="divide-y divide-neutral-100 dark:divide-neutral-800/50">
              {items.map((song, i) => (
                <SongRow
                  key={song.id}
                  song={song}
                  index={page * DEFAULT_PAGE_SIZE + i}
                  onDelete={openDelete}
                />
              ))}
            </div>
          </>
        )}
      </div>

      {totalPages > 1 && (
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <span className="text-xs text-neutral-500 dark:text-neutral-400">
            {tList("showingRange", { from: rangeFrom, to: rangeTo, total: totalElements })}
          </span>
          <div className="flex items-center gap-2">
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
