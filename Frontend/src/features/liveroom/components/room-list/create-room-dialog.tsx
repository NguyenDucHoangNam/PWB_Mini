"use client";

import { useTranslations } from "next-intl";
import { Radio } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { NEU_ACCENT_TEXT, NEU_DIALOG_CONTENT, NEU_TEXT, NEU_TEXT_MUTED } from "@/components/ui/neu";
import { CreateRoomForm } from "./create-room-form";

interface CreateRoomDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function CreateRoomDialog({ open, onOpenChange }: CreateRoomDialogProps) {
  const t = useTranslations("liveroom.create");

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {open ? (
        <DialogContent
          showCloseButton={false}
          className={`${NEU_DIALOG_CONTENT} gap-5 sm:max-w-2xl`}
        >
          <DialogHeader>
            <div className="flex items-center gap-3">
              <span
                className="neu-pressed grid size-11 shrink-0 place-items-center rounded-2xl border-none"
                aria-hidden="true"
              >
                <Radio className={`size-5 ${NEU_ACCENT_TEXT}`} />
              </span>
              <div className="flex min-w-0 flex-col gap-0.5">
                <DialogTitle className={`truncate text-lg font-bold tracking-tight ${NEU_TEXT}`}>
                  {t("title")}
                </DialogTitle>
                <DialogDescription className={`text-xs font-medium ${NEU_TEXT_MUTED}`}>
                  {t("subtitle")}
                </DialogDescription>
              </div>
            </div>
          </DialogHeader>

          <CreateRoomForm onCancel={() => onOpenChange(false)} />
        </DialogContent>
      ) : null}
    </Dialog>
  );
}
