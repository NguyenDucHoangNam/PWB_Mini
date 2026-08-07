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
import { Button } from "@/components/ui/button";
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
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("reopen")}</DialogTitle>
            <DialogDescription>
              <span className="font-medium text-foreground">{room.roomName}</span>
              <br />
              {t("emptyHint")}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="ghost" onClick={() => onOpenChange(false)} disabled={isPending}>
              {tCommon("cancel")}
            </Button>
            <Button disabled={isPending} onClick={() => reopen({ roomId: room.id })}>
              {t("reopen")}
            </Button>
          </DialogFooter>
        </DialogContent>
      ) : null}
    </Dialog>
  );
}