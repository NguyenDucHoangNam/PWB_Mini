"use client";

import { useRouter } from "next/navigation";
import { useForm, useWatch } from "react-hook-form";
import { standardSchemaResolver as zodResolver } from "@hookform/resolvers/standard-schema";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Spinner } from "@/components/ui/spinner";
import { asApiError } from "@/lib/api-client";
import { useCreateRoom } from "../api/rooms";
import { createRoomFormSchema, type CreateRoomFormValues } from "../schemas/room-schema";
import { resolveLiveroomErrorMessage } from "../lib/resolve-liveroom-error-message";
import type { CreateLiveRoomRequest, LiveRoomMode } from "../types";

const MODE_VALUES: LiveRoomMode[] = ["PUBLIC", "PRIVATE", "INVITE_ONLY", "PASSWORD"];

function localInputToIso(value: string | undefined): string | undefined {
  if (!value) return undefined;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return undefined;
  return date.toISOString();
}

export function CreateRoomForm() {
  const router = useRouter();
  const t = useTranslations("liveroom.form");
  const tActions = useTranslations("voice.actions");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("liveroom.errors");
  const tValidation = useTranslations("validation");

  const {
    register,
    handleSubmit,
    control,
    formState: { errors },
    reset,
  } = useForm<CreateRoomFormValues>({
    resolver: zodResolver(createRoomFormSchema),
    defaultValues: {
      title: "",
      description: "",
      mode: "PUBLIC",
      password: "",
      maxParticipants: 50,
      scheduledStartAt: "",
    },
  });

  const mode = useWatch({ control, name: "mode" });
  const isPasswordMode = mode === "PASSWORD";

  const { mutate: createRoom, isPending } = useCreateRoom({
    mutationConfig: {
      onSuccess: (response) => {
        if (response.success && response.data) {
          toast.success(t("createSuccess"));
          reset();
          router.push(`/dashboard/live-rooms/${response.data.roomCode}`);
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
    const payload: CreateLiveRoomRequest = {
      title: values.title.trim(),
      mode: values.mode,
      maxParticipants: Number(values.maxParticipants) || 50,
    };
    if (values.description && values.description.trim().length > 0) {
      payload.description = values.description.trim();
    }
    if (isPasswordMode && values.password && values.password.length >= 4) {
      payload.password = values.password;
    }
    const scheduledIso = localInputToIso(values.scheduledStartAt);
    if (scheduledIso) {
      payload.scheduledStartAt = scheduledIso;
    }
    createRoom({ data: payload });
  });

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-4">
      <div className="flex flex-col gap-2">
        <Label htmlFor="create-room-title">{t("titleLabel")}</Label>
        <Input
          id="create-room-title"
          {...register("title")}
          maxLength={200}
          placeholder={t("titlePlaceholder")}
        />
        {errors.title && (
          <p className="text-xs text-red-600 dark:text-red-400">
            {tValidation(errors.title.message as never)}
          </p>
        )}
      </div>

      <div className="flex flex-col gap-2">
        <Label htmlFor="create-room-description">{t("descriptionLabel")}</Label>
        <textarea
          id="create-room-description"
          {...register("description")}
          maxLength={1000}
          rows={3}
          placeholder={t("descriptionPlaceholder")}
          className="min-h-[80px] w-full rounded-lg border border-neutral-200 bg-transparent px-2.5 py-1.5 text-sm outline-none placeholder:text-neutral-400 focus:border-black focus:ring-1 focus:ring-black/30 dark:border-neutral-800 dark:bg-black dark:text-white"
        />
        {errors.description && (
          <p className="text-xs text-red-600 dark:text-red-400">
            {tValidation(errors.description.message as never)}
          </p>
        )}
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <div className="flex flex-col gap-2">
          <Label htmlFor="create-room-mode">{t("modeLabel")}</Label>
          <select
            id="create-room-mode"
            {...register("mode")}
            className="h-9 w-full rounded-md border border-neutral-200 bg-white px-3 py-1 text-sm dark:border-neutral-800 dark:bg-black dark:text-white"
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
          <Label htmlFor="create-room-capacity">{t("capacityLabel")}</Label>
          <Input
            id="create-room-capacity"
            type="number"
            min={2}
            max={500}
            {...register("maxParticipants", { valueAsNumber: true })}
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

      {isPasswordMode && (
        <div className="flex flex-col gap-2 rounded-lg border border-neutral-200 p-3 dark:border-neutral-800">
          <Label htmlFor="create-room-password">{t("passwordLabel")}</Label>
          <Input
            id="create-room-password"
            type="password"
            autoComplete="new-password"
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

      <div className="flex flex-col gap-2">
        <Label htmlFor="create-room-scheduled">{t("scheduledLabel")}</Label>
        <Input
          id="create-room-scheduled"
          type="datetime-local"
          {...register("scheduledStartAt")}
        />
        <span className="text-xs text-neutral-500 dark:text-neutral-400">
          {t("scheduledHint")}
        </span>
        {errors.scheduledStartAt && (
          <p className="text-xs text-red-600 dark:text-red-400">
            {tValidation(errors.scheduledStartAt.message as never)}
          </p>
        )}
      </div>

      {isPending && (
        <div className="flex items-center gap-2 text-sm text-neutral-500">
          <Spinner size="sm" />
          <span>{t("submitting")}</span>
        </div>
      )}

      <div className="flex justify-end gap-2">
        <Button type="button" variant="ghost" disabled={isPending} onClick={() => reset()}>
          {tActions("cancel")}
        </Button>
        <Button type="submit" disabled={isPending}>
          {t("submitCreate")}
        </Button>
      </div>
    </form>
  );
}
