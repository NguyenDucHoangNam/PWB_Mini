"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Music, Upload, SearchX, Pencil, Trash2, Check, X, Play, Pause } from "lucide-react";
import { SearchInput } from "@/components/ui/search-input";
import { Select } from "@/components/ui/select";
import { Spinner } from "@/components/ui/spinner";
import {
  NEU_ACCENT_TEXT,
  NEU_FOCUS,
  NEU_INPUT,
  NEU_TEXT,
  NEU_TEXT_MUTED,
  NEU_TEXT_SOFT,
  NeuButton,
  neuButton,
} from "@/components/ui/neu";
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
import { getSongAudioUrl, songStreamKey } from "@/features/voice/api/song-stream";
import { formatBytes, formatDuration } from "@/features/voice/lib/format-audio";
import { resolveVoiceErrorMessage } from "@/features/voice/lib/resolve-voice-error-message";
import { SONG_VIEW_STATUSES } from "@/features/voice/types";
import type { Song, SongView } from "@/features/voice/types";

const LIST_POLL_INTERVAL_MS = 5000;
const SEARCH_DEBOUNCE_MS = 250;
/** Presigned URLs outlive this by a wide margin; the point is to stop a refetch swapping
 *  `src` — and restarting the track — while someone is listening. */
const AUDIO_URL_STALE_MS = 5 * 60 * 1000;
const SEEK_STEP_PERCENT = 5;

const SONG_VIEWS = Object.keys(SONG_VIEW_STATUSES) as SongView[];

/** Only a finished upload has bytes to stream; the other two states have nothing to play. */
function isPlayable(song: Song): boolean {
  return song.status === "UPLOADED" || song.status === "PROCESSED";
}

interface RowPlayback {
  isActive: boolean;
  isPlaying: boolean;
  isLoading: boolean;
  currentTime: number;
  duration: number;
  onToggle: () => void;
  onSeek: (percent: number) => void;
}

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
  playback: RowPlayback;
}

function SongRow({ song, index, onDelete, playback }: SongRowProps) {
  const tActions = useTranslations("voice.actions");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("voice.errors");
  const tPlayer = useTranslations("voice.player");

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

  const playable = isPlayable(song);
  const progressPercent =
    playback.duration > 0 ? Math.min(100, (playback.currentTime / playback.duration) * 100) : 0;

  const seekFromClientX = (clientX: number, element: HTMLElement) => {
    const rect = element.getBoundingClientRect();
    if (rect.width === 0) return;
    playback.onSeek(((clientX - rect.left) / rect.width) * 100);
  };

  const handleSeekKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "ArrowRight") {
      e.preventDefault();
      playback.onSeek(progressPercent + SEEK_STEP_PERCENT);
    } else if (e.key === "ArrowLeft") {
      e.preventDefault();
      playback.onSeek(progressPercent - SEEK_STEP_PERCENT);
    }
  };

  return (
    <li className="group neu-tile relative isolate overflow-hidden rounded-2xl border-none">
      {!isEditing && (
        <Link
          href={`/dashboard/songs/${song.id}`}
          className="absolute inset-0 z-10 rounded-2xl focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-indigo-600 dark:focus-visible:outline-indigo-400"
        >
          <span className="sr-only">{song.title}</span>
        </Link>
      )}

      <div className="flex items-center gap-3 px-3 py-3 sm:gap-4 sm:px-4">
        <span
          className={`hidden w-5 shrink-0 text-right text-xs font-semibold tabular-nums sm:block ${NEU_TEXT_MUTED}`}
        >
          {index + 1}
        </span>

        {/* Artwork well: sunken, with a music glyph that flips to a play glyph on row hover.
            On a playable song the well *is* the transport — it sits above the row link so a
            click starts the track instead of opening the detail page. */}
        {playable ? (
          <button
            type="button"
            onClick={playback.onToggle}
            aria-label={playback.isPlaying ? tPlayer("pause") : tActions("play")}
            className={`neu-pressed-sm relative z-20 grid size-11 shrink-0 cursor-pointer place-items-center rounded-2xl border-none sm:size-12 ${NEU_FOCUS}`}
          >
            {playback.isLoading ? (
              <Spinner size="sm" />
            ) : playback.isPlaying ? (
              <Pause className={`size-5 fill-current ${NEU_ACCENT_TEXT}`} aria-hidden="true" />
            ) : playback.isActive ? (
              <Play
                className={`ml-0.5 size-5 fill-current ${NEU_ACCENT_TEXT}`}
                aria-hidden="true"
              />
            ) : (
              <>
                <Music
                  className="size-5 text-slate-500 beat-16th transition-opacity ease-hammer group-hover:opacity-0 dark:text-slate-400"
                  aria-hidden="true"
                />
                <Play
                  className="absolute ml-0.5 size-5 fill-current text-indigo-600 opacity-0 beat-16th transition-opacity ease-hammer group-hover:opacity-100 dark:text-indigo-400"
                  aria-hidden="true"
                />
              </>
            )}
          </button>
        ) : (
          <div className="neu-pressed-sm relative grid size-11 shrink-0 place-items-center rounded-2xl border-none sm:size-12">
            <Music className="size-5 text-slate-500 dark:text-slate-400" aria-hidden="true" />
          </div>
        )}

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
                className={`${NEU_INPUT} h-11 min-w-0 flex-1 rounded-xl`}
              />
              <NeuButton
                variant="primary"
                size="icon-sm"
                className="size-11 sm:size-10"
                disabled={isPending || !editValue.trim()}
                onMouseDown={(e) => {
                  e.preventDefault();
                  saveEdit();
                }}
                aria-label={tCommon("save")}
              >
                <Check className="size-4" aria-hidden="true" />
              </NeuButton>
              <NeuButton
                variant="ghost"
                size="icon-sm"
                className="size-11 sm:size-10"
                onMouseDown={(e) => {
                  e.preventDefault();
                  cancelEdit();
                }}
                aria-label={tCommon("cancel")}
              >
                <X className="size-4" aria-hidden="true" />
              </NeuButton>
            </div>
          ) : (
            <>
              <div className="flex min-w-0 items-center gap-2">
                <span
                  className={`truncate text-sm font-bold decoration-slate-400 underline-offset-4 group-hover:underline sm:text-[0.95rem] ${NEU_TEXT}`}
                >
                  {song.title}
                </span>
                <SongStatusBadge status={song.status} />
              </div>
              {/* The badge is shrink-0, so without wrapping + nowrap the leftover space breaks
                  "2.9 MB" across two lines. */}
              <div
                className={`flex flex-wrap items-center gap-x-2 gap-y-1 text-xs font-medium ${NEU_TEXT_MUTED}`}
              >
                <SongVoiceTagBadge hasVoiceTag={song.hasVoiceTag} />
                {formatLabel && <span className="hidden sm:inline">{formatLabel}</span>}
                <span aria-hidden="true" className="hidden sm:inline">
                  ·
                </span>
                <span className="whitespace-nowrap tabular-nums">{sizeLabel}</span>
                <span aria-hidden="true" className="sm:hidden">
                  ·
                </span>
                <span className="whitespace-nowrap tabular-nums sm:hidden">{durationLabel}</span>
              </div>
            </>
          )}
        </div>

        {!isEditing && (
          <>
            {/* While this row is the one playing, the same slot counts up instead of
                reporting the total — same width, so nothing shifts. */}
            <span
              className={`hidden shrink-0 text-sm font-semibold tabular-nums sm:block ${
                playback.isActive ? NEU_ACCENT_TEXT : NEU_TEXT_SOFT
              }`}
            >
              {playback.isActive ? formatDuration(playback.currentTime) : durationLabel}
            </span>

            <div className="hover-reveal relative z-20 flex shrink-0 items-center gap-1 sm:w-20 sm:justify-end">
              <NeuButton
                variant="ghost"
                size="icon-sm"
                className="size-11 sm:size-9"
                onClick={startEdit}
                aria-label={tActions("edit")}
              >
                <Pencil className="size-4" aria-hidden="true" />
              </NeuButton>
              <NeuButton
                variant="ghost"
                size="icon-sm"
                className="size-11 hover:text-rose-700 sm:size-9 dark:hover:text-rose-400"
                onClick={() => onDelete(song)}
                aria-label={tActions("delete")}
              >
                <Trash2 className="size-4" aria-hidden="true" />
              </NeuButton>
            </div>
          </>
        )}
      </div>

      {/* Playhead. Absolutely positioned along the bottom edge so the row keeps the
          height it has when nothing is playing — the tile never grows. */}
      {playback.isActive && !isEditing && (
        <div
          role="slider"
          tabIndex={0}
          aria-label={tPlayer("seek")}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={Math.round(progressPercent)}
          onClick={(e) => seekFromClientX(e.clientX, e.currentTarget)}
          onKeyDown={handleSeekKeyDown}
          className={`absolute inset-x-0 bottom-0 z-20 flex h-2.5 cursor-pointer items-end ${NEU_FOCUS}`}
        >
          <div className="h-1 w-full bg-slate-500/20 dark:bg-slate-400/20">
            <div
              className="h-full bg-indigo-600 dark:bg-indigo-400"
              style={{ width: `${progressPercent}%` }}
            />
          </div>
        </div>
      )}
    </li>
  );
}

export function DashboardSongsTab() {
  const t = useTranslations("voice.songs");
  const tStatus = useTranslations("voice.status");
  const tList = useTranslations("voice.list");
  const tPlayer = useTranslations("voice.player");

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

  // Anything that can swap the rows out also stops the audio: the row is the only
  // pause button there is, so a track must never outlive the row it belongs to.
  const setView = (value: SongView) => {
    stopPlayback();
    updateQuery({ view: value === "ALL" ? null : value, page: null });
  };

  const changeKeyword = (value: string) => {
    stopPlayback();
    setKeyword(value);
  };

  useEffect(() => {
    if (debouncedKeyword === queryFromUrl) return;
    router.replace(buildQuery({ q: debouncedKeyword || null, page: null }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedKeyword, queryFromUrl]);

  const setPage = (newPage: number) => {
    stopPlayback();
    router.push(buildPageHref(newPage));
  };

  // One <audio> for the whole list: playing a second row takes the element over, which is
  // both how "only one song at a time" is enforced and why no row owns a player of its own.
  const queryClient = useQueryClient();
  const audioRef = useRef<HTMLAudioElement>(null);
  const requestedSongId = useRef<string | null>(null);
  const [activeSongId, setActiveSongId] = useState<string | null>(null);
  const [activeUrl, setActiveUrl] = useState<string | null>(null);
  const [loadingSongId, setLoadingSongId] = useState<string | null>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [playerDuration, setPlayerDuration] = useState(0);

  const stopPlayback = () => {
    requestedSongId.current = null;
    audioRef.current?.pause();
    setActiveSongId(null);
    setActiveUrl(null);
    setLoadingSongId(null);
    setIsPlaying(false);
    setCurrentTime(0);
    setPlayerDuration(0);
  };

  // `src` only reaches the element on the render after the URL lands, so playback is
  // started here rather than in the click handler.
  useEffect(() => {
    if (!activeUrl) return;
    audioRef.current?.play().catch(() => undefined);
  }, [activeUrl]);

  const togglePlay = async (song: Song) => {
    const audio = audioRef.current;
    if (activeSongId === song.id && activeUrl) {
      if (!audio) return;
      if (audio.paused) audio.play().catch(() => undefined);
      else audio.pause();
      return;
    }

    audio?.pause();
    requestedSongId.current = song.id;
    setActiveSongId(song.id);
    setActiveUrl(null);
    setIsPlaying(false);
    setCurrentTime(0);
    // Seed the length from the listing so the bar has a scale before metadata loads.
    setPlayerDuration(song.durationSeconds ?? 0);
    setLoadingSongId(song.id);

    try {
      // Fetched through the cache rather than a `useQuery`: the URL is wanted at the moment
      // of the click, not as a standing subscription, and a cached one plays instantly.
      const response = await queryClient.fetchQuery({
        queryKey: songStreamKey(song.id),
        queryFn: () => getSongAudioUrl({ songId: song.id }),
        staleTime: AUDIO_URL_STALE_MS,
      });
      // A second click while this was in flight already owns the player.
      if (requestedSongId.current !== song.id) return;

      const url = response.success ? response.data?.url : null;
      if (!url) throw new Error("missing audio url");
      setActiveUrl(url);
      setLoadingSongId(null);
    } catch {
      if (requestedSongId.current !== song.id) return;
      toast.error(tPlayer("loadError"));
      stopPlayback();
    }
  };

  const seek = (percent: number) => {
    const audio = audioRef.current;
    if (!audio || !Number.isFinite(audio.duration) || audio.duration === 0) return;
    const clamped = Math.min(Math.max(percent, 0), 100);
    audio.currentTime = (clamped / 100) * audio.duration;
    setCurrentTime(audio.currentTime);
  };

  const openDelete = (song: Song) => {
    stopPlayback();
    updateQuery({ delete: song.id });
  };

  const closeDelete = () => {
    updateQuery({ delete: null });
  };

  return (
    <div className="flex flex-1 flex-col gap-5">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
        <div className="sm:max-w-sm sm:flex-1">
          <SearchInput
            variant="neu"
            value={keyword}
            onValueChange={changeKeyword}
            loading={searching && isFetching}
            placeholder={tList("searchSongsPlaceholder")}
            clearLabel={tList("clearSearch")}
            aria-label={tList("searchSongsPlaceholder")}
          />
        </div>

        <div className="flex items-center gap-3 sm:ml-auto">
          <Select
            options={filters}
            value={view}
            onValueChange={(v) => setView(v as SongView)}
            aria-label={tList("filterByStatus")}
            className="w-full sm:w-44"
          />

          <Link href="/dashboard/songs/new" className={neuButton({ variant: "primary" })}>
            <Upload className="size-4" aria-hidden="true" />
            {t("upload")}
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
            action={<NeuButton onClick={() => setKeyword("")}>{tList("clearSearch")}</NeuButton>}
          />
        </LibraryPanel>
      ) : items.length === 0 ? (
        <LibraryPanel>
          <LibraryEmptyState
            icon={Music}
            title={t("noSongs")}
            hint={t("emptyHint")}
            action={
              <Link href="/dashboard/songs/new" className={neuButton({ variant: "primary" })}>
                <Upload className="size-4" aria-hidden="true" />
                {t("upload")}
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
            {/* Column labels sit on the well itself — a rule here would be a border,
                and the style has none. */}
            <div
              className={`hidden items-center gap-4 px-4 pb-3 text-[11px] font-bold uppercase tracking-[0.14em] sm:flex ${NEU_TEXT_MUTED}`}
            >
              <span className="w-5 shrink-0 text-right">#</span>
              <span className="w-12 shrink-0" />
              <span className="flex-1">{tList("colTitle")}</span>
              <span className="shrink-0">{tList("colDuration")}</span>
              <span className="w-20 shrink-0" />
            </div>

            <ul className="flex flex-col gap-3">
              {items.map((song, i) => (
                <SongRow
                  key={song.id}
                  song={song}
                  index={page * DEFAULT_PAGE_SIZE + i}
                  onDelete={openDelete}
                  playback={{
                    isActive: activeSongId === song.id,
                    isPlaying: activeSongId === song.id && isPlaying,
                    isLoading: loadingSongId === song.id,
                    currentTime: activeSongId === song.id ? currentTime : 0,
                    duration: activeSongId === song.id ? playerDuration : 0,
                    onToggle: () => togglePlay(song),
                    onSeek: seek,
                  }}
                />
              ))}
            </ul>
          </div>
        </LibraryPanel>
      )}

      <LibraryPagination
        page={page}
        totalPages={totalPages}
        totalElements={totalElements}
        onPageChange={setPage}
      />

      <audio
        ref={audioRef}
        src={activeUrl ?? undefined}
        preload="metadata"
        onPlay={() => setIsPlaying(true)}
        onPause={() => setIsPlaying(false)}
        onTimeUpdate={(e) => setCurrentTime(e.currentTarget.currentTime)}
        onLoadedMetadata={(e) => {
          if (Number.isFinite(e.currentTarget.duration))
            setPlayerDuration(e.currentTarget.duration);
        }}
        onEnded={stopPlayback}
        onError={() => {
          if (!activeUrl) return;
          toast.error(tPlayer("loadError"));
          stopPlayback();
        }}
      />

      <SongDeleteDialog
        song={items.find((song) => song.id === toDeleteId) ?? null}
        open={toDeleteId !== null}
        onOpenChange={(o) => !o && closeDelete()}
      />
    </div>
  );
}
