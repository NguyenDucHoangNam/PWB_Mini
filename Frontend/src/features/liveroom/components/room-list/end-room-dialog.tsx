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
import { asApiError } from "@/lib/api-client";
import { useEndRoom, useUndoEndRoom } from "../../api/rooms";
import { resolveLiveroomErrorMessage } from "../../lib/resolve-liveroom-error-message";
import type { Room } from "../../types";

const UNDO_TOAST_MS = 5000;

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

  const { mutate: undoEnd } = useUndoEndRoom({
    mutationConfig: {
      onSuccess: () => {
        toast.success(t("revivedToast"));
      },
      onError: asApiError((err) => {
        toast.error(resolveLiveroomErrorMessage(err, tErrors, tCommon));
      }),
    },
  });

  const { mutate: end, isPending } = useEndRoom({
    mutationConfig: {
      onSuccess: (response, variables) => {
        onOpenChange(false);
        onSuccess?.();
        if (response.data?.canUndoEnd) {
          toast.success(t("endedToast"), {
            duration: UNDO_TOAST_MS,
            action: {
              label: t("undo"),
              onClick: () => undoEnd({ roomId: variables.roomId }),
            },
          });
        } else {
          toast.success(t("endedToast"));
        }
      },
      onError: asApiError((err) => {
        toast.error(resolveLiveroomErrorMessage(err, tErrors, tCommon));
      }),
    },
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {open && room ? (
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("endConfirmTitle")}</DialogTitle>
            <DialogDescription>
              <span className="font-medium text-black dark:text-white">{room.roomName}</span>
              <br />
              {t("endConfirmBody")}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="ghost" onClick={() => onOpenChange(false)} disabled={isPending}>
              {tCommon("cancel")}
            </Button>
            <Button
              variant="destructive"
              disabled={isPending}
              onClick={() => end({ roomId: room.id })}
            >
              {t("end")}
            </Button>
          </DialogFooter>
        </DialogContent>
      ) : null}
    </Dialog>
  );
}