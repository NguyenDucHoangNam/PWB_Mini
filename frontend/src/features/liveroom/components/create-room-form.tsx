"use client";

import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { standardSchemaResolver as zodResolver } from "@hookform/resolvers/standard-schema";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Spinner } from "@/components/ui/spinner";
import { asApiError } from "@/lib/api-client";
import { useCreateRoom } from "../api/rooms";
import {
  createRoomFormSchema,
  type CreateRoomFormValues,
} from "../schemas/room-schema";
import { resolveLiveroomErrorMessage } from "../lib/resolve-liveroom-error-message";
import type { CreateLiveRoomRequest } from "../types";

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
    formState: { errors },
    reset,
  } = useForm<CreateRoomFormValues>({
    resolver: zodResolver(createRoomFormSchema),
    defaultValues: {
      title: "",
      description: "",
      mode: "PUBLIC",
      maxParticipants: 5,
    },
  });

  const { mutate: createRoom, isPending } = useCreateRoom({
    mutationConfig: {
      onSuccess: (response) => {
        if (response.success && response.data) {
          toast.success(t("createSuccess"));
          reset();
          router.push(`/live-rooms/${response.data.roomCode}`);
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
      maxParticipants: Number(values.maxParticipants) || 5,
    };
    if (values.description && values.description.trim().length > 0) {
      payload.description = values.description.trim();
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

      <div className="flex flex-col gap-2">
        <Label htmlFor="create-room-mode">{t("modeLabel")}</Label>
        <select
          id="create-room-mode"
          {...register("mode")}
          className="flex h-9 w-full rounded-lg border border-neutral-200 bg-transparent px-3 py-1.5 text-sm outline-none focus:border-black focus:ring-1 focus:ring-black/30 dark:border-neutral-800 dark:bg-black dark:text-white"
        >
          <option value="PUBLIC">{t("modePublic")}</option>
          <option value="PRIVATE">{t("modePrivate")}</option>
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
          max={5}
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
