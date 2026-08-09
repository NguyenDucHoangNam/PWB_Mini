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
import { NEU_TEXT, NEU_TEXT_MUTED, NeuButton } from "@/components/ui/neu";
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
        <DialogContent
          showCloseButton={false}
          className="neu-raised gap-6 rounded-3xl border-none bg-[#e0e5ec] p-6 ring-0 dark:bg-[#1e222b]"
        >
          <DialogHeader>
            <DialogTitle className={`text-lg font-bold ${NEU_TEXT}`}>
              {t("confirmTitle")}
            </DialogTitle>
            <DialogDescription className={NEU_TEXT_MUTED}>
              <span className={`font-bold ${NEU_TEXT}`}>{song.title}</span>
              <br />
              {t("confirmMessage")}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter className="mx-0 mb-0 gap-3 border-t-0 bg-transparent p-0">
            <NeuButton onClick={() => onOpenChange(false)} disabled={isPending}>
              {tCommon("cancel")}
            </NeuButton>
            <NeuButton
              variant="danger"
              disabled={isPending}
              onClick={() => deleteSong({ songId: song.id })}
            >
              {t("confirmButton")}
            </NeuButton>
          </DialogFooter>
        </DialogContent>
      ) : null}
    </Dialog>
  );
}
