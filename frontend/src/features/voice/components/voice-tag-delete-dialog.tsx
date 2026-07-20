"use client";

import { useState } from "react";
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
import { useDeleteVoiceTag } from "@/features/voice/api/voice-tags";
import { asApiError } from "@/lib/api-client";
import { resolveVoiceErrorMessage } from "@/features/voice/lib/resolve-voice-error-message";

interface VoiceTagDeleteDialogProps {
  voiceTagId: string | null;
  voiceTagName: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess?: () => void;
}

export function VoiceTagDeleteDialog({
  voiceTagId,
  voiceTagName,
  open,
  onOpenChange,
  onSuccess,
}: VoiceTagDeleteDialogProps) {
  const t = useTranslations("voice.voiceTags.delete");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("voice.errors");
  const router = useRouter();

  const { mutate: deleteTag, isPending } = useDeleteVoiceTag({
    mutationConfig: {
      onSuccess: (response) => {
        if (response.success) {
          toast.success(t("confirmButton"));
          onOpenChange(false);
          onSuccess?.();
          router.refresh();
        } else {
          toast.error(response.message || tCommon("error"));
        }
      },
      onError: asApiError((err) => {
        toast.error(resolveVoiceErrorMessage(err, tErrors, tCommon));
      }),
    },
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {open ? (
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("confirmTitle")}</DialogTitle>
            <DialogDescription>
              <span className="font-medium text-black dark:text-white">{voiceTagName}</span>
              <br />
              {t("confirmMessage")}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="ghost" onClick={() => onOpenChange(false)} disabled={isPending}>
              {tCommon("cancel")}
            </Button>
            <Button
              variant="destructive"
              disabled={isPending || !voiceTagId}
              onClick={() => voiceTagId && deleteTag({ voiceTagId })}
            >
              {t("confirmButton")}
            </Button>
          </DialogFooter>
        </DialogContent>
      ) : null}
    </Dialog>
  );
}
