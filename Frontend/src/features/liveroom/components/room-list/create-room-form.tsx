"use client";

import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { standardSchemaResolver as zodResolver } from "@hookform/resolvers/standard-schema";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { asApiError } from "@/lib/api-client";
import { useCreateRoom } from "../../api/rooms";
import { resolveLiveroomErrorMessage } from "../../lib/resolve-liveroom-error-message";
import {
  ROOM_NAME_MAX_LENGTH,
  createRoomSchema,
  type CreateRoomFormValues,
} from "../../schemas/create-room-schema";
import {
  ROOM_DEFAULT_CAPACITY,
  ROOM_DEFAULT_GRACE_SECONDS,
  ROOM_MAX_CAPACITY,
  ROOM_MAX_GRACE_SECONDS,
  ROOM_MIN_CAPACITY,
  ROOM_MIN_GRACE_SECONDS,
} from "../../types";

const CAPACITY_OPTIONS = Array.from(
  { length: ROOM_MAX_CAPACITY - ROOM_MIN_CAPACITY + 1 },
  (_, index) => ROOM_MIN_CAPACITY + index,
);

const GRACE_PRESETS = [30, 60, 300, 900];

export function CreateRoomForm() {
  const t = useTranslations("liveroom.create");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("liveroom.errors");
  const tValidation = useTranslations("validation");
  const router = useRouter();

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors },
  } = useForm<CreateRoomFormValues>({
    resolver: zodResolver(createRoomSchema),
    defaultValues: {
      roomName: "",
      maxParticipants: ROOM_DEFAULT_CAPACITY,
      ownerGraceSeconds: ROOM_DEFAULT_GRACE_SECONDS,
    },
  });

  const capacity = watch("maxParticipants");
  const grace = watch("ownerGraceSeconds");

  const { mutate: create, isPending } = useCreateRoom({
    mutationConfig: {
      onSuccess: (response) => {
        toast.success(t("success"));
        if (response.data) router.push(`/liveroom/${response.data.id}`);
        else router.push("/dashboard/liveroom");
      },
      onError: asApiError((err) => {
        toast.error(resolveLiveroomErrorMessage(err, tErrors, tCommon));
      }),
    },
  });

  const renderNameError = () => {
    const message = errors.roomName?.message ?? "";
    if (message === "validation.name.maxlength") {
      return tValidation("name.maxlength", { max: ROOM_NAME_MAX_LENGTH });
    }
    if (message === "validation.name.required") return tValidation("name.required");
    return message;
  };

  return (
    <form
      onSubmit={handleSubmit((values) => create({ data: values }))}
      className="flex w-full flex-col gap-6"
      noValidate
    >
      <div className="flex flex-col gap-2">
        <Label htmlFor="liveroom-name">{t("roomName")}</Label>
        <Input
          id="liveroom-name"
          {...register("roomName")}
          maxLength={ROOM_NAME_MAX_LENGTH}
          placeholder={t("roomNamePlaceholder")}
          aria-invalid={Boolean(errors.roomName)}
          className="h-11 md:h-9"
        />
        <p className="text-xs text-neutral-500 dark:text-neutral-400">{t("roomNameHint")}</p>
        {errors.roomName ? (
          <p className="text-xs text-red-600 dark:text-red-400">{renderNameError()}</p>
        ) : null}
      </div>

      <fieldset className="flex flex-col gap-2">
        <legend className="mb-2 text-sm leading-none font-medium">
          {t("maxParticipants")}
        </legend>
        <div className="flex flex-wrap gap-2">
          {CAPACITY_OPTIONS.map((option) => (
            <button
              key={option}
              type="button"
              aria-pressed={capacity === option}
              onClick={() =>
                setValue("maxParticipants", option, { shouldValidate: true })
              }
              className={`size-11 rounded-lg border text-sm font-medium transition-colors md:size-9 ${
                capacity === option
                  ? "border-black bg-black text-white dark:border-white dark:bg-white dark:text-black"
                  : "border-neutral-200 bg-white text-neutral-700 hover:border-neutral-400 dark:border-neutral-800 dark:bg-black dark:text-neutral-300"
              }`}
            >
              {option}
            </button>
          ))}
        </div>
        <p className="text-xs text-neutral-500 dark:text-neutral-400">
          {t("maxParticipantsHint")}
        </p>
        {errors.maxParticipants ? (
          <p className="text-xs text-red-600 dark:text-red-400">{t("capacityInvalid")}</p>
        ) : null}
      </fieldset>

      <div className="flex flex-col gap-2">
        <Label htmlFor="liveroom-grace">{t("graceSeconds")}</Label>
        <div className="flex flex-wrap gap-2">
          {GRACE_PRESETS.map((preset) => (
            <button
              key={preset}
              type="button"
              aria-pressed={grace === preset}
              onClick={() =>
                setValue("ownerGraceSeconds", preset, { shouldValidate: true })
              }
              className={`h-11 rounded-lg border px-3 text-sm font-medium transition-colors md:h-9 ${
                grace === preset
                  ? "border-black bg-black text-white dark:border-white dark:bg-white dark:text-black"
                  : "border-neutral-200 bg-white text-neutral-700 hover:border-neutral-400 dark:border-neutral-800 dark:bg-black dark:text-neutral-300"
              }`}
            >
              {preset}s
            </button>
          ))}
        </div>
        <Input
          id="liveroom-grace"
          type="number"
          inputMode="numeric"
          min={ROOM_MIN_GRACE_SECONDS}
          max={ROOM_MAX_GRACE_SECONDS}
          {...register("ownerGraceSeconds", { valueAsNumber: true })}
          aria-invalid={Boolean(errors.ownerGraceSeconds)}
          className="h-11 md:h-9"
        />
        <p className="text-xs text-neutral-500 dark:text-neutral-400">{t("graceHint")}</p>
        {errors.ownerGraceSeconds ? (
          <p className="text-xs text-red-600 dark:text-red-400">{t("graceInvalid")}</p>
        ) : null}
      </div>

      <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
        <Button
          type="button"
          variant="ghost"
          className="h-11 md:h-9"
          disabled={isPending}
          onClick={() => router.push("/dashboard/liveroom")}
        >
          {t("cancel")}
        </Button>
        <Button type="submit" className="h-11 md:h-9" disabled={isPending}>
          {isPending ? <Loader2 className="size-4 animate-spin" /> : null}
          {isPending ? t("submitting") : t("submit")}
        </Button>
      </div>
    </form>
  );
}