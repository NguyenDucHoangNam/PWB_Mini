"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useUpdateSong } from "@/features/voice/api/songs";
import { asApiError } from "@/lib/api-client";
import { resolveVoiceErrorMessage } from "@/features/voice/lib/resolve-voice-error-message";

interface SongEditDialogProps {
  songId: string | null;
  currentTitle: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function SongEditDialog({
  songId,
  currentTitle,
  open,
  onOpenChange,
}: SongEditDialogProps) {
  const tActions = useTranslations("voice.actions");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("voice.errors");
  const router = useRouter();

  const [title, setTitle] = useState(currentTitle);

  // Reset on the closed→open transition so a discarded edit does not survive into the next opening.
  // Adjusting during render rather than in an effect avoids rendering the stale value first.
  const [wasOpen, setWasOpen] = useState(open);
  if (open !== wasOpen) {
    setWasOpen(open);
    if (open) setTitle(currentTitle);
  }

  const { mutate: updateSong, isPending } = useUpdateSong({
    mutationConfig: {
      onSuccess: (response) => {
        if (response.success) {
          toast.success(tCommon("save"));
          onOpenChange(false);
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

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!songId || !title.trim() || isPending) return;
    updateSong({
      songId,
      data: { title: title.trim() },
    });
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {open ? (
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{tActions("edit")}</DialogTitle>
          </DialogHeader>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-2">
              <Input
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                maxLength={200}
                autoFocus
              />
            </div>
            <DialogFooter>
              <Button
                type="button"
                variant="ghost"
                onClick={() => onOpenChange(false)}
                disabled={isPending}
              >
                {tCommon("cancel")}
              </Button>
              <Button
                type="submit"
                disabled={isPending || !title.trim() || !songId}
              >
                {tCommon("save")}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      ) : null}
    </Dialog>
  );
}
