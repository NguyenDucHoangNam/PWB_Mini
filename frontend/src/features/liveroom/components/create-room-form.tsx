"use client";

import { useRouter } from "next/navigation";
import { useForm, useWatch } from "react-hook-form";
import { standardSchemaResolver as zodResolver } from "@hookform/resolvers/standard-schema";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Globe, Lock } from "lucide-react";
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
    setValue,
    watch,
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
        <Label>{t("modeLabel")}</Label>
        <div className="flex gap-3">
          <button
            type="button"
            onClick={() => setValue("mode", "PUBLIC")}
            className={`flex flex-1 items-center gap-2 rounded-lg border p-3 transition-colors ${
              watch("mode") === "PUBLIC"
                ? "border-primary bg-primary/5 dark:bg-primary/10"
                : "border-neutral-200 hover:border-neutral-300 dark:border-neutral-700 dark:hover:border-neutral-600"
            }`}
          >
            <Globe
              className={`h-5 w-5 ${
                watch("mode") === "PUBLIC"
                  ? "text-primary"
                  : "text-neutral-500"
              }`}
            />
            <div className="flex flex-col items-start text-left">
              <span
                className={`text-sm font-medium ${
                  watch("mode") === "PUBLIC"
                    ? "text-primary"
                    : "text-neutral-700 dark:text-neutral-300"
                }`}
              >
                {t("modePublic")}
              </span>
            </div>
          </button>
          <button
            type="button"
            onClick={() => setValue("mode", "PRIVATE")}
            className={`flex flex-1 items-center gap-2 rounded-lg border p-3 transition-colors ${
              watch("mode") === "PRIVATE"
                ? "border-primary bg-primary/5 dark:bg-primary/10"
                : "border-neutral-200 hover:border-neutral-300 dark:border-neutral-700 dark:hover:border-neutral-600"
            }`}
          >
            <Lock
              className={`h-5 w-5 ${
                watch("mode") === "PRIVATE"
                  ? "text-primary"
                  : "text-neutral-500"
              }`}
            />
            <div className="flex flex-col items-start text-left">
              <span
                className={`text-sm font-medium ${
                  watch("mode") === "PRIVATE"
                    ? "text-primary"
                    : "text-neutral-700 dark:text-neutral-300"
                }`}
              >
                {t("modePrivate")}
              </span>
            </div>
          </button>
        </div>
        <input type="hidden" {...register("mode")} />
        {errors.mode && (
          <p className="text-xs text-red-600 dark:text-red-400">
            {tValidation(errors.mode.message as never)}
          </p>
        )}
      </div>

      <div className="flex flex-col gap-2">
        <Label>{t("capacityLabel")}</Label>
        <div className="flex gap-2">
          {[2, 3, 4, 5].map((n) => (
            <button
              key={n}
              type="button"
              onClick={() => setValue("maxParticipants", n)}
              className={`flex h-10 w-12 items-center justify-center rounded-lg border text-sm font-medium transition-colors ${
                watch("maxParticipants") === n
                  ? "border-primary bg-primary text-primary-foreground"
                  : "border-neutral-200 bg-transparent text-neutral-700 hover:border-neutral-300 dark:border-neutral-700 dark:text-neutral-300 dark:hover:border-neutral-600"
              }`}
            >
              {n}
            </button>
          ))}
        </div>
        <input
          type="hidden"
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
