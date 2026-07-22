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
import { Spinner } from "@/components/ui/spinner";
import { useState } from "react";
import { useEndRoom } from "../api/rooms";
import { resolveLiveroomErrorMessage } from "../lib/resolve-liveroom-error-message";
import type { LiveRoomSummary } from "../types";

interface RoomEndDialogProps {
  room: LiveRoomSummary | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function RoomEndDialog({ room, open, onOpenChange }: RoomEndDialogProps) {
  const t = useTranslations("liveroom.endDialog");
  const tErrors = useTranslations("liveroom.errors");
  const tCommon = useTranslations("common");

  const [pending, setPending] = useState(false);

  const { mutate: endRoom } = useEndRoom({
    mutationConfig: {
      onSuccess: (response) => {
        setPending(false);
        if (response.success) {
          toast.success(t("success"));
          onOpenChange(false);
        } else {
          toast.error(response.message || tCommon("error"));
        }
      },
      onError: (err) => {
        setPending(false);
        toast.error(resolveLiveroomErrorMessage(err, tErrors, tCommon));
      },
    },
  });

  const handleConfirm = () => {
    if (!room) return;
    setPending(true);
    endRoom({ roomCode: room.roomCode });
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {open && room ? (
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("title")}</DialogTitle>
            <DialogDescription>{t("message")}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              variant="ghost"
              type="button"
              onClick={() => onOpenChange(false)}
              disabled={pending}
            >
              {tCommon("cancel")}
            </Button>
            <Button
              variant="destructive"
              type="button"
              onClick={handleConfirm}
              disabled={pending}
            >
              {pending ? (
                <span className="flex items-center gap-2">
                  <Spinner size="sm" />
                  {tCommon("loading")}
                </span>
              ) : (
                t("confirm")
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      ) : null}
    </Dialog>
  );
}
