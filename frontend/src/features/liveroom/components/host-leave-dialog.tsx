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

interface HostLeaveDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onLeaveOnly: () => void;
  onEndRoom: () => void;
  isLeaving?: boolean;
  isEnding?: boolean;
}

export function HostLeaveDialog({
  open,
  onOpenChange,
  onLeaveOnly,
  onEndRoom,
  isLeaving,
  isEnding,
}: HostLeaveDialogProps) {
  const t = useTranslations("liveroom.hostLeaveDialog");

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {open ? (
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("title")}</DialogTitle>
            <DialogDescription>{t("message")}</DialogDescription>
          </DialogHeader>
          <DialogFooter className="flex-col gap-2 sm:flex-col">
            <Button
              variant="outline"
              type="button"
              onClick={() => {
                onLeaveOnly();
                onOpenChange(false);
              }}
              disabled={isLeaving}
              className="w-full"
            >
              {isLeaving ? t("leaving") : t("leaveOnly")}
            </Button>
            <Button
              variant="destructive"
              type="button"
              onClick={() => {
                onEndRoom();
                onOpenChange(false);
              }}
              disabled={isEnding}
              className="w-full"
            >
              {isEnding ? t("ending") : t("endRoom")}
            </Button>
            <Button
              variant="ghost"
              type="button"
              onClick={() => onOpenChange(false)}
              disabled={isLeaving || isEnding}
              className="w-full"
            >
              {t("cancel")}
            </Button>
          </DialogFooter>
        </DialogContent>
      ) : null}
    </Dialog>
  );
}
