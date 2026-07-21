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
import { asApiError } from "@/lib/api-client";
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
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("liveroom.errors");
  const tActions = useTranslations("liveroom.actions");

  const { mutate: endRoom, isPending } = useEndRoom({
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

  const handleConfirm = () => {
    if (!room) return;
    endRoom(
      { roomCode: room.roomCode },
      {
        onSuccess: (response) => {
          if (response.success) {
            toast.success(t("success"));
            onOpenChange(false);
          } else {
            toast.error(response.message || tCommon("error"));
          }
        },
      },
    );
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {open && room ? (
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("title")}</DialogTitle>
            <DialogDescription>
              <span className="font-medium text-black dark:text-white">{room.title}</span>
              <br />
              {t("message")}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="ghost" onClick={() => onOpenChange(false)} disabled={isPending}>
              {tActions("cancel")}
            </Button>
            <Button
              variant="destructive"
              disabled={isPending}
              onClick={handleConfirm}
            >
              {isPending ? (
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
