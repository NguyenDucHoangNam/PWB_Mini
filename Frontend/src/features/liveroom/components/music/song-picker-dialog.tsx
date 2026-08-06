"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { Music2, SearchX } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { SearchInput } from "@/components/ui/search-input";
import { Spinner } from "@/components/ui/spinner";
import { useDebouncedValue } from "@/hooks/use-debounced-value";
import { useListSongs, useSearchSongs } from "@/features/voice/api/songs";
import { SONG_VIEW_STATUSES } from "@/features/voice/types";

const SEARCH_DEBOUNCE_MS = 250;
const PAGE_SIZE = 50;

interface SongPickerDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onPick: (songId: string) => void;
}

export function SongPickerDialog({ open, onOpenChange, onPick }: SongPickerDialogProps) {
  const t = useTranslations("liveroom.room.music");
  const tList = useTranslations("voice.list");

  const [keyword, setKeyword] = useState("");
  const debouncedKeyword = useDebouncedValue(keyword, SEARCH_DEBOUNCE_MS);
  const searching = debouncedKeyword.trim().length > 0;



  const readyStatuses = SONG_VIEW_STATUSES.READY;

  const listQuery = useListSongs({
    page: 0,
    size: PAGE_SIZE,
    status: readyStatuses,
    queryConfig: { enabled: open && !searching },
  });

  const searchQuery = useSearchSongs({
    page: 0,
    size: PAGE_SIZE,
    q: debouncedKeyword,
    status: readyStatuses,
    queryConfig: { enabled: open && searching },
  });

  const { data, isPending, isFetching } = searching ? searchQuery : listQuery;
  const songs = data?.data?.content ?? [];

  const close = (next: boolean) => {
    if (!next) setKeyword("");
    onOpenChange(next);
  };

  return (
    <Dialog open={open} onOpenChange={close}>
      {open ? (
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("mySongs")}</DialogTitle>
          </DialogHeader>

          <SearchInput
            autoFocus
            value={keyword}
            onValueChange={setKeyword}
            loading={searching && isFetching}
            placeholder={tList("searchSongsPlaceholder")}
            clearLabel={tList("clearSearch")}
            aria-label={tList("searchSongsPlaceholder")}
          />

          {isPending ? (
            <div className="flex items-center justify-center gap-2 py-10 text-sm text-neutral-500 dark:text-neutral-400">
              <Spinner size="sm" />
            </div>
          ) : songs.length === 0 ? (
            <div className="flex flex-col items-center gap-2 py-10 text-center text-sm text-neutral-500 dark:text-neutral-400">
              {searching ? (
                <>
                  <SearchX className="size-6 text-neutral-400" aria-hidden />
                  <p>{tList("noResults")}</p>
                </>
              ) : (
                <p>{t("noSongs")}</p>
              )}
            </div>
          ) : (
            <ul className="max-h-[50vh] overflow-y-auto">
              {songs.map((song) => (
                <li key={song.id}>
                  <button
                    type="button"
                    onClick={() => {
                      onPick(song.id);
                      close(false);
                    }}
                    className="flex w-full items-center gap-3 rounded-lg px-2 py-3 text-left hover:bg-neutral-100 dark:hover:bg-neutral-900"
                  >
                    <Music2 className="size-4 shrink-0 text-neutral-400" aria-hidden />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-medium text-black dark:text-white">
                        {song.title}
                      </span>
                      {song.artist ? (
                        <span className="block truncate text-xs text-neutral-500 dark:text-neutral-400">
                          {t("by", { artist: song.artist })}
                        </span>
                      ) : null}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </DialogContent>
      ) : null}
    </Dialog>
  );
}