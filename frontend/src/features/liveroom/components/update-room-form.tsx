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
import { updateRoomFormSchema, type UpdateRoomFormValues } from "../schemas/room-schema";
import { resolveLiveroomErrorMessage } from "../lib/resolve-liveroom-error-message";
import type { LiveRoom, LiveRoomMode, UpdateLiveRoomSettingsRequest } from "../types";

const MODE_VALUES: LiveRoomMode[] = ["PUBLIC", "PRIVATE", "INVITE_ONLY", "PASSWORD"];

interface UpdateRoomFormProps {
  room: LiveRoom;
}

export function UpdateRoomForm({ room }: UpdateRoomFormProps) {
  const t = useTranslations("liveroom.form");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("liveroom.errors");
  const tValidation = useTranslations("validation");

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
      mode: room.mode,
      password: "",
      maxParticipants: room.maxParticipants,
    },
  });

  useEffect(() => {
    reset({
      title: room.title,
      description: room.description ?? "",
      mode: room.mode,
      password: "",
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
    if (values.mode && values.mode !== room.mode) {
      payload.mode = values.mode;
    }
    if (values.password && values.password.length >= 4) {
      payload.password = values.password;
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
          disabled={isTerminal}
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
          disabled={isTerminal}
          className="min-h-[80px] w-full rounded-lg border border-neutral-200 bg-transparent px-2.5 py-1.5 text-sm outline-none placeholder:text-neutral-400 focus:border-black focus:ring-1 focus:ring-black/30 disabled:cursor-not-allowed disabled:opacity-60 dark:border-neutral-800 dark:bg-black dark:text-white"
        />
        {errors.description && (
          <p className="text-xs text-red-600 dark:text-red-400">
            {tValidation(errors.description.message as never)}
          </p>
        )}
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <div className="flex flex-col gap-2">
          <Label htmlFor="update-room-mode">{t("modeLabel")}</Label>
          <select
            id="update-room-mode"
            {...register("mode")}
            disabled={isTerminal}
            className="h-9 w-full rounded-md border border-neutral-200 bg-white px-3 py-1 text-sm disabled:cursor-not-allowed disabled:opacity-60 dark:border-neutral-800 dark:bg-black dark:text-white"
          >
            {MODE_VALUES.map((m) => (
              <option key={m} value={m}>
                {m}
              </option>
            ))}
          </select>
          {errors.mode && (
            <p className="text-xs text-red-600 dark:text-red-400">
              {tValidation(errors.mode.message as never)}
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
            disabled={isTerminal}
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
      </div>

      {!isTerminal && (
        <div className="flex flex-col gap-2 rounded-lg border border-neutral-200 p-3 dark:border-neutral-800">
          <Label htmlFor="update-room-password">{t("passwordLabel")}</Label>
          <Input
            id="update-room-password"
            type="password"
            autoComplete="new-password"
            placeholder={
              room.passwordProtected ? "•".repeat(8) : t("passwordLabel")
            }
            {...register("password")}
            maxLength={64}
          />
          <span className="text-xs text-neutral-500 dark:text-neutral-400">
            {t("passwordHint")}
          </span>
          {errors.password && (
            <p className="text-xs text-red-600 dark:text-red-400">
              {tValidation(errors.password.message as never)}
            </p>
          )}
        </div>
      )}

      <div className="flex justify-end gap-2">
        <Button type="submit" disabled={isPending || isTerminal}>
          {isPending ? (
            <span className="flex items-center gap-2">
              <Spinner size="sm" />
              {t("submitting")}
            </span>
          ) : (
            t("submitUpdate")
          )}
        </Button>
      </div>
    </form>
  );
}
