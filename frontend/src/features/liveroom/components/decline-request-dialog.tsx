"use client";

import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { standardSchemaResolver as zodResolver } from "@hookform/resolvers/standard-schema";
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
import { Label } from "@/components/ui/label";
import { Spinner } from "@/components/ui/spinner";
import { useRejectJoinRequest } from "../api/join-requests";
import {
  declineRequestFormSchema,
  type DeclineRequestFormValues,
} from "../schemas/room-schema";
import type { LiveRoomJoinRequest } from "../types";

interface DeclineRequestDialogProps {
  roomCode: string;
  request: LiveRoomJoinRequest | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onConfirm?: (reason: string) => void;
  onSettled?: () => void;
}

export function DeclineRequestDialog({
  roomCode,
  request,
  open,
  onOpenChange,
  onConfirm,
  onSettled,
}: DeclineRequestDialogProps) {
  const t = useTranslations("liveroom.declineDialog");
  const tCommon = useTranslations("common");

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<DeclineRequestFormValues>({
    resolver: zodResolver(declineRequestFormSchema),
    defaultValues: { reason: "" },
  });

  useEffect(() => {
    if (!open) reset({ reason: "" });
  }, [open, reset]);

  const { mutate: rejectJoinRequest, isPending } = useRejectJoinRequest();

  const onSubmit = handleSubmit((values) => {
    if (!request) return;
    const trimmed = values.reason?.trim() ?? "";
    if (onConfirm) {
      onConfirm(trimmed);
      return;
    }
    rejectJoinRequest(
      {
        roomCode,
        requestId: request.id,
        data: trimmed.length > 0 ? { reason: trimmed } : {},
      },
      {
        onSuccess: () => {
          onOpenChange(false);
          onSettled?.();
        },
      },
    );
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {open && request ? (
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("title")}</DialogTitle>
            <DialogDescription>
              {t("message", { name: request.displayName })}
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={onSubmit} className="flex flex-col gap-3">
            <div className="flex flex-col gap-2">
              <Label htmlFor="decline-reason">{t("reasonLabel")}</Label>
              <textarea
                id="decline-reason"
                {...register("reason")}
                maxLength={500}
                rows={3}
                placeholder={t("reasonPlaceholder")}
                disabled={isPending}
                className="min-h-[80px] w-full rounded-lg border border-neutral-200 bg-transparent px-2.5 py-1.5 text-sm outline-none placeholder:text-neutral-400 focus:border-black focus:ring-1 focus:ring-black/30 dark:border-neutral-800 dark:bg-black dark:text-white"
              />
              {errors.reason && (
                <p className="text-xs text-red-600 dark:text-red-400">
                  {errors.reason.message}
                </p>
              )}
            </div>

            <DialogFooter>
              <Button
                variant="ghost"
                type="button"
                onClick={() => onOpenChange(false)}
                disabled={isPending}
              >
                {t("cancel")}
              </Button>
              <Button type="submit" variant="destructive" disabled={isPending}>
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
          </form>
        </DialogContent>
      ) : null}
    </Dialog>
  );
}
