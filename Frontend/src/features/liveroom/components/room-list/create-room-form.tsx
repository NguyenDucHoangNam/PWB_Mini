"use client";

import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { standardSchemaResolver as zodResolver } from "@hookform/resolvers/standard-schema";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Loader2 } from "lucide-react";
import {
  NEU_ERROR_TEXT,
  NEU_FOCUS,
  NEU_INPUT,
  NEU_LABEL,
  NEU_TEXT_MUTED,
  NeuButton,
} from "@/components/ui/neu";
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

interface CreateRoomFormProps {
  onCancel?: () => void;
}

export function CreateRoomForm({ onCancel }: CreateRoomFormProps) {
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
      className="flex w-full flex-col gap-4"
      noValidate
    >
      <div className="flex flex-col gap-1.5">
        <label htmlFor="liveroom-name" className={NEU_LABEL}>
          {t("roomName")}
        </label>
        <input
          id="liveroom-name"
          {...register("roomName")}
          maxLength={ROOM_NAME_MAX_LENGTH}
          placeholder={t("roomNamePlaceholder")}
          aria-invalid={Boolean(errors.roomName)}
          className={`${NEU_INPUT} h-11`}
        />
        {errors.roomName ? (
          <p role="alert" className={NEU_ERROR_TEXT}>
            {renderNameError()}
          </p>
        ) : (
          <p className={`text-xs ${NEU_TEXT_MUTED}`}>{t("roomNameHint")}</p>
        )}
      </div>

      <div className="grid gap-4 sm:grid-cols-2 sm:gap-5">
        <fieldset className="flex flex-col gap-1.5">
          <legend className={`mb-1.5 ${NEU_LABEL}`}>{t("maxParticipants")}</legend>
          <div className="flex flex-wrap gap-1.5">
            {CAPACITY_OPTIONS.map((option) => (
              <button
                key={option}
                type="button"
                aria-pressed={capacity === option}
                onClick={() => setValue("maxParticipants", option, { shouldValidate: true })}
                className={`size-9 rounded-xl border-none text-sm font-bold tabular-nums transition-all ${NEU_FOCUS} ${
                  capacity === option
                    ? "neu-pressed text-indigo-600 dark:text-indigo-400"
                    : "neu-button"
                }`}
              >
                {option}
              </button>
            ))}
          </div>
          {errors.maxParticipants ? (
            <p role="alert" className={NEU_ERROR_TEXT}>
              {t("capacityInvalid")}
            </p>
          ) : (
            <p className={`text-xs ${NEU_TEXT_MUTED}`}>{t("maxParticipantsHint")}</p>
          )}
        </fieldset>

        <div className="flex flex-col gap-1.5">
          <label htmlFor="liveroom-grace" className={`mb-1.5 ${NEU_LABEL}`}>
            {t("graceSeconds")}
          </label>
          <div className="flex flex-wrap items-center gap-1.5">
            {GRACE_PRESETS.map((preset) => (
              <button
                key={preset}
                type="button"
                aria-pressed={grace === preset}
                onClick={() => setValue("ownerGraceSeconds", preset, { shouldValidate: true })}
                className={`h-9 rounded-xl border-none px-2.5 text-xs font-bold tabular-nums transition-all ${NEU_FOCUS} ${
                  grace === preset
                    ? "neu-pressed text-indigo-600 dark:text-indigo-400"
                    : "neu-button"
                }`}
              >
                {preset}s
              </button>
            ))}
            <input
              id="liveroom-grace"
              type="number"
              inputMode="numeric"
              min={ROOM_MIN_GRACE_SECONDS}
              max={ROOM_MAX_GRACE_SECONDS}
              {...register("ownerGraceSeconds", { valueAsNumber: true })}
              aria-invalid={Boolean(errors.ownerGraceSeconds)}
              className={`${NEU_INPUT} h-9 w-16 rounded-xl px-2 tabular-nums`}
            />
          </div>
          {errors.ownerGraceSeconds ? (
            <p role="alert" className={NEU_ERROR_TEXT}>
              {t("graceInvalid")}
            </p>
          ) : (
            <p className={`text-xs ${NEU_TEXT_MUTED}`}>{t("graceHint")}</p>
          )}
        </div>
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:justify-end">
        <NeuButton
          type="button"
          size="sm"
          disabled={isPending}
          onClick={() => (onCancel ? onCancel() : router.push("/dashboard/liveroom"))}
        >
          {t("cancel")}
        </NeuButton>
        <NeuButton type="submit" size="sm" variant="primary" disabled={isPending}>
          {isPending ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : null}
          {isPending ? t("submitting") : t("submit")}
        </NeuButton>
      </div>
    </form>
  );
}
