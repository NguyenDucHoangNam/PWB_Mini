"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { useDeleteSong } from "@/features/voice/api/songs";
import { asApiError } from "@/lib/api-client";
import { resolveErrorI18nKey } from "@/lib/error-code-to-i18n";
import type { Song } from "../types";

interface SongDeleteDialogProps {
  song: Song | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function SongDeleteDialog({ song, open, onOpenChange }: SongDeleteDialogProps) {
  const t = useTranslations("voice.songs.delete");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("voice.errors");
  const router = useRouter();

  const { mutate: deleteSong, isPending } = useDeleteSong({
    mutationConfig: {
      onSuccess: (response) => {
        if (response.success) {
          toast.success(t("confirmButton"));
          onOpenChange(false);
          router.refresh();
        }
      },
      onError: asApiError((err) => {
        const key = resolveErrorI18nKey(err);
        toast.error(key ? tErrors(key.split(".").pop() as never) : tCommon("error"));
      }),
    },
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {open && song ? (
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("confirmTitle")}</DialogTitle>
            <DialogDescription>
              <span className="font-medium text-black dark:text-white">{song.title}</span>
              <br />
              {t("confirmMessage")}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="ghost" onClick={() => onOpenChange(false)} disabled={isPending}>
              {tCommon("cancel")}
            </Button>
            <Button
              variant="destructive"
              disabled={isPending}
              onClick={() => deleteSong({ songId: song.id })}
            >
              {t("confirmButton")}
            </Button>
          </DialogFooter>
        </DialogContent>
      ) : null}
    </Dialog>
  );
}