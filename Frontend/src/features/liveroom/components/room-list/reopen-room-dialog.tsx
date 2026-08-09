"use client";

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
import {
  NEU_DIALOG_CONTENT,
  NEU_DIALOG_FOOTER,
  NEU_TEXT,
  NEU_TEXT_MUTED,
  NeuButton,
} from "@/components/ui/neu";
import { asApiError } from "@/lib/api-client";
import { useReopenRoom } from "../../api/rooms";
import { resolveLiveroomErrorMessage } from "../../lib/resolve-liveroom-error-message";
import type { Room } from "../../types";

interface ReopenRoomDialogProps {
  room: Room | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function ReopenRoomDialog({ room, open, onOpenChange }: ReopenRoomDialogProps) {
  const t = useTranslations("liveroom.list");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("liveroom.errors");
  const router = useRouter();

  const { mutate: reopen, isPending } = useReopenRoom({
    mutationConfig: {
      onSuccess: (response) => {
        toast.success(t("reopenedToast"));
        onOpenChange(false);
        if (response.data) router.push(`/liveroom/${response.data.id}`);
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
            <DialogTitle className={`text-lg font-bold ${NEU_TEXT}`}>{t("reopen")}</DialogTitle>
            <DialogDescription className={NEU_TEXT_MUTED}>
              <span className={`font-bold ${NEU_TEXT}`}>{room.roomName}</span>
              <br />
              {t("emptyHint")}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter className={NEU_DIALOG_FOOTER}>
            <NeuButton onClick={() => onOpenChange(false)} disabled={isPending}>
              {tCommon("cancel")}
            </NeuButton>
            <NeuButton variant="primary" disabled={isPending} onClick={() => reopen({ roomId: room.id })}>
              {t("reopen")}
            </NeuButton>
          </DialogFooter>
        </DialogContent>
      ) : null}
    </Dialog>
  );
}