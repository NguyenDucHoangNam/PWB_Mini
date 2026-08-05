"use client";

import { useTranslations } from "next-intl";
import { Music2 } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Spinner } from "@/components/ui/spinner";
import { useListSongs } from "@/features/voice/api/songs";

interface SongPickerDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onPick: (songId: string) => void;
}

export function SongPickerDialog({ open, onOpenChange, onPick }: SongPickerDialogProps) {
  const t = useTranslations("liveroom.room.music");
  const { data, isPending } = useListSongs({
    page: 0,
    size: 50,
    status: "PROCESSED",
    queryConfig: { enabled: open },
  });

  const songs = data?.data?.content ?? [];

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {open ? (
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("mySongs")}</DialogTitle>
          </DialogHeader>
          {isPending ? (
            <div className="flex items-center justify-center gap-2 py-10 text-sm text-neutral-500 dark:text-neutral-400">
              <Spinner size="sm" />
            </div>
          ) : songs.length === 0 ? (
            <p className="py-10 text-center text-sm text-neutral-500 dark:text-neutral-400">
              {t("noSongs")}
            </p>
          ) : (
            <ul className="max-h-[50vh] overflow-y-auto">
              {songs.map((song) => (
                <li key={song.id}>
                  <button
                    type="button"
                    onClick={() => {
                      onPick(song.id);
                      onOpenChange(false);
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