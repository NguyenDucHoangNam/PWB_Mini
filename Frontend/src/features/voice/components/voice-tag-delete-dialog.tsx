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
import {
  NEU_DIALOG_CONTENT,
  NEU_DIALOG_FOOTER,
  NEU_TEXT,
  NEU_TEXT_MUTED,
  NeuButton,
} from "@/components/ui/neu";
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

  const { mutate: deleteTag, isPending } = useDeleteVoiceTag({
    mutationConfig: {
      onSuccess: () => {
        toast.success(t("deleteSuccess"));
        onOpenChange(false);
        onSuccess?.();
      },
      onError: asApiError((err) => {
        toast.error(resolveVoiceErrorMessage(err, tErrors, tCommon));
      }),
    },
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {open ? (
        <DialogContent
          showCloseButton={false}
          className={NEU_DIALOG_CONTENT}
        >
          <DialogHeader>
            <DialogTitle className={`text-lg font-bold ${NEU_TEXT}`}>
              {t("confirmTitle")}
            </DialogTitle>
            <DialogDescription className={NEU_TEXT_MUTED}>
              <span className={`font-bold ${NEU_TEXT}`}>{voiceTagName}</span>
              <br />
              {t("confirmMessage")}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter className={NEU_DIALOG_FOOTER}>
            <NeuButton onClick={() => onOpenChange(false)} disabled={isPending}>
              {tCommon("cancel")}
            </NeuButton>
            <NeuButton
              variant="danger"
              disabled={isPending || !voiceTagId}
              onClick={() => voiceTagId && deleteTag({ voiceTagId })}
            >
              {t("confirmButton")}
            </NeuButton>
          </DialogFooter>
        </DialogContent>
      ) : null}
    </Dialog>
  );
}
