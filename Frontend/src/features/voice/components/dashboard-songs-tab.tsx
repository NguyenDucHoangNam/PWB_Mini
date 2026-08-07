"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Music, Upload, SearchX, Pencil, Trash2, Check, X, Play } from "lucide-react";
import { Button } from "@/components/ui/button";
import { SearchInput } from "@/components/ui/search-input";
import { Select } from "@/components/ui/select";
import { useDebouncedValue } from "@/hooks/use-debounced-value";
import { DEFAULT_PAGE_SIZE } from "@/lib/constants";
import { asApiError } from "@/lib/api-client";
import { SongDeleteDialog } from "@/features/voice/components/song-delete-dialog";
import { SongStatusBadge, SongVoiceTagBadge } from "@/features/voice/components/song-status-badge";
import {
  LibraryEmptyState,
  LibraryErrorState,
  LibraryPagination,
  LibraryPanel,
  LibraryRowsSkeleton,
} from "@/features/voice/components/library-states";
import { useListSongs, useSearchSongs, useUpdateSong } from "@/features/voice/api/songs";
import { formatBytes, formatDuration } from "@/features/voice/lib/format-audio";
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

interface SongRowProps {
  song: Song;
  index: number;
  onDelete: (song: Song) => void;
}

function SongRow({ song, index, onDelete }: SongRowProps) {
  const tActions = useTranslations("voice.actions");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("voice.errors");

  const [isEditing, setIsEditing] = useState(false);
  const [editValue, setEditValue] = useState(song.title);
  const inputRef = useRef<HTMLInputElement>(null);

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

  const durationLabel = song.durationSeconds !== null ? formatDuration(song.durationSeconds) : "—";
  const sizeLabel = song.fileSizeBytes !== null ? formatBytes(song.fileSizeBytes) : "—";
  const formatLabel = (song.format ?? "").toUpperCase();

  return (
    <li className="group relative isolate beat-16th transition-colors ease-hammer hover:bg-muted/40">
      {!isEditing && (
        <Link
          href={`/dashboard/songs/${song.id}`}
          className="absolute inset-0 z-10 rounded-lg focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-ring"
        >
          <span className="sr-only">{song.title}</span>
        </Link>
      )}

      <div className="flex items-center gap-3 px-3 py-2.5 sm:gap-4 sm:px-4 sm:py-3">
        <span className="hidden w-5 shrink-0 text-right text-xs tabular-nums text-muted-foreground sm:block">
          {index + 1}
        </span>

        {/* Artwork tile: a music glyph that flips to a play glyph on row hover. */}
        <div className="relative grid size-11 shrink-0 place-items-center overflow-hidden rounded-lg border border-border bg-gradient-to-br from-muted/70 to-muted/30 sm:size-12">
          <Music
            className="size-5 text-muted-foreground beat-16th transition-opacity ease-hammer group-hover:opacity-0"
            aria-hidden="true"
          />
          <Play
            className="absolute size-5 fill-current text-foreground opacity-0 beat-16th transition-opacity ease-hammer group-hover:opacity-100"
            aria-hidden="true"
          />
        </div>

        <div className="flex min-w-0 flex-1 flex-col gap-1">
          {isEditing ? (
            <div className="flex items-center gap-1.5">
              <input
                ref={inputRef}
                type="text"
                value={editValue}
                onChange={(e) => setEditValue(e.target.value)}
                onKeyDown={handleKeyDown}
                onBlur={cancelEdit}
                maxLength={200}
                disabled={isPending}
                aria-label={tActions("edit")}
                className="h-9 min-w-0 flex-1 rounded-lg border border-input bg-background px-2.5 text-sm font-medium text-foreground outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 sm:h-8"
              />
              <Button
                variant="secondary"
                size="icon"
                className="size-9 shrink-0 sm:size-8"
                disabled={isPending || !editValue.trim()}
                onMouseDown={(e) => {
                  e.preventDefault();
                  saveEdit();
                }}
                aria-label={tCommon("save")}
              >
                <Check className="size-4" aria-hidden="true" />
              </Button>
              <Button
                variant="ghost"
                size="icon"
                className="size-9 shrink-0 sm:size-8"
                onMouseDown={(e) => {
                  e.preventDefault();
                  cancelEdit();
                }}
                aria-label={tCommon("cancel")}
              >
                <X className="size-4" aria-hidden="true" />
              </Button>
            </div>
          ) : (
            <>
              <div className="flex min-w-0 items-center gap-2">
                <span className="truncate text-sm font-semibold text-foreground decoration-muted-foreground/40 underline-offset-4 group-hover:underline sm:text-[0.95rem]">
                  {song.title}
                </span>
                <SongStatusBadge status={song.status} />
              </div>
              <div className="flex items-center gap-2 text-xs text-muted-foreground">
                <SongVoiceTagBadge hasVoiceTag={song.hasVoiceTag} />
                {formatLabel && <span className="hidden sm:inline">{formatLabel}</span>}
                <span aria-hidden="true" className="hidden sm:inline">·</span>
                <span className="tabular-nums">{sizeLabel}</span>
                <span aria-hidden="true" className="sm:hidden">·</span>
                <span className="tabular-nums sm:hidden">{durationLabel}</span>
              </div>
            </>
          )}
        </div>

        {!isEditing && (
          <>
            <span className="hidden shrink-0 text-sm tabular-nums text-foreground sm:block">
              {durationLabel}
            </span>

            <div className="hover-reveal relative z-20 flex shrink-0 items-center gap-0.5 sm:w-18 sm:justify-end">
              <Button
                variant="ghost"
                size="icon"
                className="size-11 text-muted-foreground hover:text-foreground sm:size-8"
                onClick={startEdit}
                aria-label={tActions("edit")}
              >
                <Pencil className="size-4 sm:size-3.5" aria-hidden="true" />
              </Button>
              <Button
                variant="ghost"
                size="icon"
                className="size-11 text-muted-foreground hover:text-destructive sm:size-8"
                onClick={() => onDelete(song)}
                aria-label={tActions("delete")}
              >
                <Trash2 className="size-4 sm:size-3.5" aria-hidden="true" />
              </Button>
            </div>
          </>
        )}
      </div>
    </li>
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
    <div className="flex flex-1 flex-col gap-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <div className="sm:max-w-sm sm:flex-1">
          <SearchInput
            value={keyword}
            onValueChange={setKeyword}
            loading={searching && isFetching}
            placeholder={tList("searchSongsPlaceholder")}
            clearLabel={tList("clearSearch")}
            aria-label={tList("searchSongsPlaceholder")}
            className="h-11 sm:h-9"
          />
        </div>

        <div className="flex items-center gap-2 sm:ml-auto">
          <Select
            options={filters}
            value={view}
            onValueChange={(v) => setView(v as SongView)}
            aria-label={tList("filterByStatus")}
            className="h-11 w-full sm:h-9 sm:w-40"
          />

          <Link href="/dashboard/songs/new" className="shrink-0">
            <Button size="lg" className="h-11 gap-2 font-semibold sm:h-9">
              <Upload className="size-4" aria-hidden="true" />
              {t("upload")}
            </Button>
          </Link>
        </div>
      </div>


      {isLoading ? (
        <LibraryPanel>
          <LibraryRowsSkeleton />
        </LibraryPanel>
      ) : isError ? (
        <LibraryPanel>
          <LibraryErrorState
            message={t("errorLoad")}
            retryLabel={t("retry")}
            onRetry={() => refetch()}
          />
        </LibraryPanel>
      ) : items.length === 0 && searching ? (
        <LibraryPanel>
          <LibraryEmptyState
            icon={SearchX}
            title={tList("noResults")}
            hint={tList("noResultsHint", { query: debouncedKeyword })}
            action={
              <Button variant="outline" size="lg" onClick={() => setKeyword("")}>
                {tList("clearSearch")}
              </Button>
            }
          />
        </LibraryPanel>
      ) : items.length === 0 ? (
        <LibraryPanel>
          <LibraryEmptyState
            icon={Music}
            title={t("noSongs")}
            hint={t("emptyHint")}
            action={
              <Link href="/dashboard/songs/new">
                <Button size="lg" className="gap-2">
                  <Upload className="size-4" aria-hidden="true" />
                  {t("upload")}
                </Button>
              </Link>
            }
          />
        </LibraryPanel>
      ) : (
        <LibraryPanel>
          <div
            aria-busy={isFetching}
            className={`beat-8th transition-opacity ${isFetching ? "opacity-70" : ""}`}
          >
            <div className="hidden items-center gap-4 border-b border-border bg-muted/40 px-4 py-2.5 text-xs font-medium uppercase tracking-wide text-muted-foreground sm:flex">
              <span className="w-5 shrink-0 text-right">#</span>
              <span className="w-12 shrink-0" />
              <span className="flex-1">{tList("colTitle")}</span>
              <span className="shrink-0">{tList("colDuration")}</span>
              <span className="w-18 shrink-0" />
            </div>

            <ul className="divide-y divide-border">
              {items.map((song, i) => (
                <SongRow
                  key={song.id}
                  song={song}
                  index={page * DEFAULT_PAGE_SIZE + i}
                  onDelete={openDelete}
                />
              ))}
            </ul>
          </div>
        </LibraryPanel>
      )}

      <LibraryPagination
        page={page}
        totalPages={totalPages}
        rangeFrom={rangeFrom}
        rangeTo={rangeTo}
        totalElements={totalElements}
        onPageChange={setPage}
      />

      <SongDeleteDialog
        song={items.find((song) => song.id === toDeleteId) ?? null}
        open={toDeleteId !== null}
        onOpenChange={(o) => !o && closeDelete()}
      />
    </div>
  );
}
