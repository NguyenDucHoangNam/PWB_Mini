"use client";

import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { standardSchemaResolver as zodResolver } from "@hookform/resolvers/standard-schema";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Spinner } from "@/components/ui/spinner";
import { asApiError } from "@/lib/api-client";
import { useUpdateRoom } from "../api/rooms";
import {
  updateRoomFormSchema,
  type UpdateRoomFormValues,
} from "../schemas/room-schema";
import { resolveLiveroomErrorMessage } from "../lib/resolve-liveroom-error-message";
import type { LiveRoom, UpdateLiveRoomSettingsRequest } from "../types";

interface UpdateRoomFormProps {
  room: LiveRoom;
}

export function UpdateRoomForm({ room }: UpdateRoomFormProps) {
  const t = useTranslations("liveroom.form");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("liveroom.errors");
  const tValidation = useTranslations("validation");
  const tActions = useTranslations("voice.actions");

  const {
    register,
    handleSubmit,
    formState: { errors },
    reset,
  } = useForm<UpdateRoomFormValues>({
    resolver: zodResolver(updateRoomFormSchema),
    defaultValues: {
      title: room.title,
      description: room.description ?? "",
      maxParticipants: room.maxParticipants,
    },
  });

  useEffect(() => {
    reset({
      title: room.title,
      description: room.description ?? "",
      maxParticipants: room.maxParticipants,
    });
  }, [room, reset]);

  const { mutate: updateRoom, isPending } = useUpdateRoom({
    mutationConfig: {
      onSuccess: (response) => {
        if (response.success) {
          toast.success(t("updateSuccess"));
        } else {
          toast.error(response.message || tCommon("error"));
        }
      },
      onError: asApiError((err) => {
        toast.error(resolveLiveroomErrorMessage(err, tErrors, tCommon));
      }),
    },
  });

  const onSubmit = handleSubmit((values) => {
    const payload: UpdateLiveRoomSettingsRequest = {};
    if (values.title && values.title.trim().length > 0 && values.title.trim() !== room.title) {
      payload.title = values.title.trim();
    }
    if (values.description !== undefined && values.description !== (room.description ?? "")) {
      payload.description = values.description.trim().length > 0 ? values.description.trim() : "";
    }
    if (
      values.maxParticipants !== undefined &&
      Number(values.maxParticipants) !== room.maxParticipants
    ) {
      payload.maxParticipants = Number(values.maxParticipants);
    }
    updateRoom({ roomCode: room.roomCode, data: payload });
  });

  const isTerminal = room.status === "ENDED";

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-4">
      <div className="flex flex-col gap-2">
        <Label htmlFor="update-room-title">{t("titleLabel")}</Label>
        <Input
          id="update-room-title"
          {...register("title")}
          maxLength={200}
          placeholder={t("titlePlaceholder")}
          disabled={isTerminal || isPending}
        />
        {errors.title && (
          <p className="text-xs text-red-600 dark:text-red-400">
            {tValidation(errors.title.message as never)}
          </p>
        )}
      </div>

      <div className="flex flex-col gap-2">
        <Label htmlFor="update-room-description">{t("descriptionLabel")}</Label>
        <textarea
          id="update-room-description"
          {...register("description")}
          maxLength={1000}
          rows={3}
          placeholder={t("descriptionPlaceholder")}
          disabled={isTerminal || isPending}
          className="min-h-[80px] w-full rounded-lg border border-neutral-200 bg-transparent px-2.5 py-1.5 text-sm outline-none placeholder:text-neutral-400 focus:border-black focus:ring-1 focus:ring-black/30 dark:border-neutral-800 dark:bg-black dark:text-white"
        />
        {errors.description && (
          <p className="text-xs text-red-600 dark:text-red-400">
            {tValidation(errors.description.message as never)}
          </p>
        )}
      </div>

      <div className="flex flex-col gap-2">
        <Label htmlFor="update-room-capacity">{t("capacityLabel")}</Label>
        <Input
          id="update-room-capacity"
          type="number"
          min={2}
          max={500}
          {...register("maxParticipants", { valueAsNumber: true })}
          disabled={isTerminal || isPending}
        />
        <span className="text-xs text-neutral-500 dark:text-neutral-400">
          {t("capacityHint")}
        </span>
        {errors.maxParticipants && (
          <p className="text-xs text-red-600 dark:text-red-400">
            {tValidation(errors.maxParticipants.message as never)}
          </p>
        )}
      </div>

      {isPending && (
        <div className="flex items-center gap-2 text-sm text-neutral-500">
          <Spinner size="sm" />
          <span>{t("submitting")}</span>
        </div>
      )}

      {!isTerminal && (
        <div className="flex justify-end gap-2">
          <Button
            type="button"
            variant="ghost"
            disabled={isPending}
            onClick={() => {
              reset({
                title: room.title,
                description: room.description ?? "",
                maxParticipants: room.maxParticipants,
              });
            }}
          >
            {tActions("cancel")}
          </Button>
          <Button type="submit" disabled={isPending}>
            {t("submitUpdate")}
          </Button>
        </div>
      )}
    </form>
  );
}
