"use client";

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
import { resolveVoiceErrorMessage } from "@/features/voice/lib/resolve-voice-error-message";
import type { Song } from "../types";

interface SongDeleteDialogProps {
  song: Song | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Lets a detail page navigate away — the song it renders no longer exists. */
  onSuccess?: () => void;
}

export function SongDeleteDialog({
  song,
  open,
  onOpenChange,
  onSuccess,
}: SongDeleteDialogProps) {
  const t = useTranslations("voice.songs.delete");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("voice.errors");

  const { mutate: deleteSong, isPending } = useDeleteSong({
    mutationConfig: {
      onSuccess: () => {
        toast.success(t("deleteSuccess"));
        onOpenChange(false);
        onSuccess?.();
      },
      onError: asApiError((err) => {
        toast.error(resolveVoiceErrorMessage(err, tErrors, tCommon));
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
