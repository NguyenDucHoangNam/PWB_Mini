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
import {
  NEU_DIALOG_CONTENT,
  NEU_DIALOG_FOOTER,
  NEU_TEXT,
  NEU_TEXT_MUTED,
  NeuButton,
} from "@/components/ui/neu";
import { asApiError } from "@/lib/api-client";
import { useEndRoom } from "../../api/rooms";
import { resolveLiveroomErrorMessage } from "../../lib/resolve-liveroom-error-message";
import type { Room } from "../../types";

interface EndRoomDialogProps {
  room: Room | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess?: () => void;
}

export function EndRoomDialog({ room, open, onOpenChange, onSuccess }: EndRoomDialogProps) {
  const t = useTranslations("liveroom.room.header");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("liveroom.errors");

  const { mutate: end, isPending } = useEndRoom({
    mutationConfig: {
      onSuccess: () => {
        onOpenChange(false);
        onSuccess?.();
        toast.success(t("endedToast"));
      },
      onError: asApiError((err) => {
        toast.error(resolveLiveroomErrorMessage(err, tErrors, tCommon));
      }),
    },
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {open && room ? (
        <DialogContent showCloseButton={false} className={NEU_DIALOG_CONTENT}>
          <DialogHeader>
            <DialogTitle className={`text-lg font-bold ${NEU_TEXT}`}>{t("endConfirmTitle")}</DialogTitle>
            <DialogDescription className={NEU_TEXT_MUTED}>
              <span className={`font-bold ${NEU_TEXT}`}>{room.roomName}</span>
              <br />
              {t("endConfirmBody")}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter className={NEU_DIALOG_FOOTER}>
            <NeuButton onClick={() => onOpenChange(false)} disabled={isPending}>
              {tCommon("cancel")}
            </NeuButton>
            <NeuButton
              variant="danger"
              disabled={isPending}
              onClick={() => end({ roomId: room.id })}
            >
              {t("end")}
            </NeuButton>
          </DialogFooter>
        </DialogContent>
      ) : null}
    </Dialog>
  );
}