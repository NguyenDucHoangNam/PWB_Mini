"use client";

import { useTranslations } from "next-intl";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";

interface LeaveRoomDialogProps {
  open: boolean;
  pending: boolean;
  onOpenChange: (open: boolean) => void;
  onConfirm: () => void;
}

export function LeaveRoomDialog({
  open,
  pending,
  onOpenChange,
  onConfirm,
}: LeaveRoomDialogProps) {
  const t = useTranslations("liveroom.room.header");
  const tCommon = useTranslations("common");

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {open ? (
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("leaveConfirmTitle")}</DialogTitle>
            <DialogDescription>{t("leaveConfirmBody")}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="ghost" onClick={() => onOpenChange(false)} disabled={pending}>
              {tCommon("cancel")}
            </Button>
            <Button variant="destructive" disabled={pending} onClick={onConfirm}>
              {t("leave")}
            </Button>
          </DialogFooter>
        </DialogContent>
      ) : null}
    </Dialog>
  );
}