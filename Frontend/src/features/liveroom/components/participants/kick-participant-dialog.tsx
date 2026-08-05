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
import { useKickParticipant } from "../../api/participants";
import { resolveLiveroomErrorMessage } from "../../lib/resolve-liveroom-error-message";
import { displayName } from "../../utils/participant-sort";
import type { Participant } from "../../types";

interface KickParticipantDialogProps {
  roomId: string;
  participant: Participant | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function KickParticipantDialog({
  roomId,
  participant,
  open,
  onOpenChange,
}: KickParticipantDialogProps) {
  const t = useTranslations("liveroom.room.participants");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("liveroom.errors");

  const { mutate: kick, isPending } = useKickParticipant({
    mutationConfig: {
      onSuccess: (response) => {
        toast.success(response.message);
        onOpenChange(false);
      },
      onError: asApiError((err) => {
        toast.error(resolveLiveroomErrorMessage(err, tErrors, tCommon));
      }),
    },
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {open && participant ? (
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("kickConfirmTitle", { name: displayName(participant) })}</DialogTitle>
            <DialogDescription>{t("kickConfirmBody")}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="ghost" onClick={() => onOpenChange(false)} disabled={isPending}>
              {tCommon("cancel")}
            </Button>
            <Button
              variant="destructive"
              disabled={isPending}
              onClick={() => kick({ roomId, targetUserId: participant.userId })}
            >
              {t("kick")}
            </Button>
          </DialogFooter>
        </DialogContent>
      ) : null}
    </Dialog>
  );
}