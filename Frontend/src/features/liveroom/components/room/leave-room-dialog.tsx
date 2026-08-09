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
import {
  NEU_DIALOG_CONTENT,
  NEU_DIALOG_FOOTER,
  NEU_TEXT,
  NEU_TEXT_MUTED,
  NeuButton,
} from "@/components/ui/neu";

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
        <DialogContent showCloseButton={false} className={NEU_DIALOG_CONTENT}>
          <DialogHeader>
            <DialogTitle className={`text-lg font-bold ${NEU_TEXT}`}>{t("leaveConfirmTitle")}</DialogTitle>
            <DialogDescription className={NEU_TEXT_MUTED}>{t("leaveConfirmBody")}</DialogDescription>
          </DialogHeader>
          <DialogFooter className={NEU_DIALOG_FOOTER}>
            <NeuButton onClick={() => onOpenChange(false)} disabled={pending}>
              {tCommon("cancel")}
            </NeuButton>
            <NeuButton variant="danger" disabled={pending} onClick={onConfirm}>
              {t("leave")}
            </NeuButton>
          </DialogFooter>
        </DialogContent>
      ) : null}
    </Dialog>
  );
}