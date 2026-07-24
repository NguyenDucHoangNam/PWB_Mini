"use client";

import { useEffect, useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { Music } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useListSongs } from "@/features/voice/api/songs";
import { useSelectPlaybackSong } from "../api/playback";
import type { Song } from "@/features/voice/types";
import { resolveLiveroomErrorMessage } from "../lib/resolve-liveroom-error-message";
import { asApiError } from "@/lib/api-client";
import { toast } from "sonner";

interface SongPickerDialogProps {
  open: boolean;
  roomCode: string;
  onOpenChange: (open: boolean) => void;
  onPicked?: () => void;
}

export function SongPickerDialog({
  open,
  roomCode,
  onOpenChange,
  onPicked,
}: SongPickerDialogProps) {
  const t = useTranslations("liveroom.playback.picker");
  const tErrors = useTranslations("liveroom.errors");
  const tCommon = useTranslations("common");

  const [pickedSongId, setPickedSongId] = useState<string | null>(null);

  const list = useListSongs({
    page: 0,
    size: 50,
    queryConfig: {
      enabled: open,
    },
  });

  const songs: Song[] = useMemo(() => {
    const res = list.data;
    if (!res?.success || !res.data) {
      return [];
    }
    return (res.data.content ?? []).filter(
      (song) => song.status === "UPLOADED" || song.status === "PROCESSED",
    );
  }, [list.data]);

  const mutation = useSelectPlaybackSong({
    mutationConfig: {
      onError: asApiError((err) => {
        toast.error(
          resolveLiveroomErrorMessage(
            err,
            (k) => tErrors(k as never),
            (k) => tCommon(k as never),
          ),
        );
      }),
    },
  });

  useEffect(() => {
    if (!open) {
      setPickedSongId(null);
    }
  }, [open]);

  const handleSelect = () => {
    if (!pickedSongId) {
      return;
    }
    mutation.mutate(
      { roomCode, songId: pickedSongId },
      {
        onSuccess: (response) => {
          if (response.success) {
            onPicked?.();
            onOpenChange(false);
          }
        },
      },
    );
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {open ? (
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <Music className="size-4" />
              {t("title")}
            </DialogTitle>
            <DialogDescription>{t("subtitle")}</DialogDescription>
          </DialogHeader>

          <div className="max-h-80 space-y-2 overflow-y-auto">
            {list.isLoading ? (
              <div className="flex items-center justify-center py-8 text-sm text-neutral-500">
                <Spinner size="sm" />
                <span className="ml-2">{t("loading")}</span>
              </div>
            ) : songs.length === 0 ? (
              <div className="rounded border border-dashed border-neutral-300 px-4 py-6 text-center text-sm text-neutral-500 dark:border-neutral-700">
                {t("empty")}
              </div>
            ) : (
              songs.map((song) => {
                const selected = pickedSongId === song.id;
                const hasVoiceTag = song.status === "PROCESSED";
                return (
                  <button
                    key={song.id}
                    type="button"
                    onClick={() => setPickedSongId(song.id)}
                    className={`flex w-full items-center justify-between gap-3 rounded-lg border px-3 py-2 text-left text-sm transition ${
                      selected
                        ? "border-blue-500 bg-blue-500/10"
                        : "border-neutral-300 hover:bg-neutral-100 dark:border-neutral-700 dark:hover:bg-neutral-800"
                    }`}
                  >
                    <span className="flex min-w-0 flex-1 flex-col">
                      <span className="truncate font-medium">{song.title}</span>
                      {song.artist ? (
                        <span className="truncate text-xs text-neutral-500">
                          {song.artist}
                        </span>
                      ) : null}
                    </span>
                    <span
                      className={`shrink-0 rounded-full px-2 py-0.5 text-[10px] uppercase tracking-wide ${
                        hasVoiceTag
                          ? "bg-emerald-500/15 text-emerald-700 dark:text-emerald-300"
                          : "bg-neutral-500/15 text-neutral-600 dark:text-neutral-400"
                      }`}
                    >
                      {hasVoiceTag ? t("voiceTag") : t("original")}
                    </span>
                  </button>
                );
              })
            )}
          </div>

          <DialogFooter className="gap-2">
            <Button
              type="button"
              variant="ghost"
              onClick={() => onOpenChange(false)}
              disabled={mutation.isPending}
            >
              {t("cancel")}
            </Button>
            <Button
              type="button"
              onClick={handleSelect}
              disabled={!pickedSongId || mutation.isPending}
            >
              {mutation.isPending ? t("submitting") : t("submit")}
            </Button>
          </DialogFooter>
        </DialogContent>
      ) : null}
    </Dialog>
  );
}
