"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { standardSchemaResolver as zodResolver } from "@hookform/resolvers/standard-schema";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { asApiError } from "@/lib/api-client";
import { resolveErrorI18nKey } from "@/lib/error-code-to-i18n";
import { useConfigureVoiceTag, useRemoveVoiceTagConfig } from "../api/songs";
import { useListVoiceTags } from "../api/voice-tags";
import {
  configFormSchema,
  DEFAULT_CONFIG_VALUES,
  type ConfigFormValues,
} from "../schemas/config-schema";
import type { VoiceTagConfig } from "../types";

interface VoiceTagConfigFormProps {
  songId: string;
  config: VoiceTagConfig | null;
}

function getDefaultValues(config: VoiceTagConfig | null): ConfigFormValues {
  if (!config) return DEFAULT_CONFIG_VALUES;
  return {
    voiceTagId: config.voiceTagId,
    intervalSeconds: config.intervalSeconds,
    volumePercentage: config.volumePercentage,
    fadeInDurationMs: config.fadeInDurationMs,
    fadeOutDurationMs: config.fadeOutDurationMs,
    startOffsetSeconds: config.startOffsetSeconds,
    enabled: config.enabled,
  };
}

function resolveValidationError(
  message: string | undefined,
  params: Record<string, string | number>,
  tValidation: (key: string, params?: Record<string, string | number>) => string,
): string | null {
  if (!message) return null;
  if (params && Object.keys(params).length > 0) {
    return tValidation(message, params);
  }
  return tValidation(message);
}

export function VoiceTagConfigForm({ songId, config }: VoiceTagConfigFormProps) {
  const t = useTranslations("voice.config");
  const tActions = useTranslations("voice.actions");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("voice.errors");
  const tValidation = useTranslations("validation");
  const [showForm, setShowForm] = useState(false);

  const { data: tagsRes, isLoading: tagsLoading } = useListVoiceTags({
    page: 0,
    size: 100,
  });

  const {
    register,
    handleSubmit,
    formState: { errors },
    watch,
    setValue,
    reset,
  } = useForm<ConfigFormValues>({
    resolver: zodResolver(configFormSchema),
    defaultValues: getDefaultValues(config),
  });

  const interval = watch("intervalSeconds");
  const volume = watch("volumePercentage");
  const fadeIn = watch("fadeInDurationMs");
  const fadeOut = watch("fadeOutDurationMs");
  const startOffset = watch("startOffsetSeconds");
  const enabled = watch("enabled");
  const voiceTagId = watch("voiceTagId");

  const { mutate: configure, isPending } = useConfigureVoiceTag({
    mutationConfig: {
      onSuccess: (response) => {
        if (response.success) {
          toast.success(t("saveSuccess"));
          setShowForm(false);
        }
      },
      onError: asApiError((err) => {
        const key = resolveErrorI18nKey(err);
        toast.error(key ? tErrors(key.split(".").pop() as never) : tCommon("error"));
      }),
    },
  });

  const { mutate: remove, isPending: removePending } = useRemoveVoiceTagConfig({
    mutationConfig: {
      onSuccess: (response) => {
        if (response.success) {
          toast.success(t("removeSuccess"));
          setShowForm(false);
          reset(DEFAULT_CONFIG_VALUES);
        }
      },
      onError: asApiError((err) => {
        const key = resolveErrorI18nKey(err);
        toast.error(key ? tErrors(key.split(".").pop() as never) : tCommon("error"));
      }),
    },
  });

  const onSubmit = handleSubmit((values) => {
    configure({ songId, data: values });
  });

  const tags = tagsRes?.success && tagsRes.data ? tagsRes.data.content : [];

  if (config && !showForm) {
    return (
      <div className="flex flex-col gap-3 rounded-xl border border-neutral-200 bg-white p-4 dark:border-neutral-800 dark:bg-black">
        <h3 className="text-sm font-semibold text-black dark:text-white">{t("title")}</h3>
        <dl className="grid gap-x-4 gap-y-2 text-xs text-neutral-600 dark:text-neutral-400 sm:grid-cols-2">
          <div className="flex flex-col">
            <dt className="text-[11px] uppercase tracking-wide text-neutral-500">
              {t("voiceTag")}
            </dt>
            <dd className="font-medium text-black dark:text-white">{config.voiceTagName}</dd>
          </div>
          <div className="flex flex-col">
            <dt className="text-[11px] uppercase tracking-wide text-neutral-500">
              {t("intervalSeconds")}
            </dt>
            <dd className="font-medium text-black dark:text-white">{config.intervalSeconds}s</dd>
          </div>
          <div className="flex flex-col">
            <dt className="text-[11px] uppercase tracking-wide text-neutral-500">
              {t("volumePercentage")}
            </dt>
            <dd className="font-medium text-black dark:text-white">{config.volumePercentage}%</dd>
          </div>
          <div className="flex flex-col">
            <dt className="text-[11px] uppercase tracking-wide text-neutral-500">
              {t("fadeInMs")} / {t("fadeOutMs")}
            </dt>
            <dd className="font-medium text-black dark:text-white">
              {config.fadeInDurationMs}ms / {config.fadeOutDurationMs}ms
            </dd>
          </div>
          <div className="flex flex-col">
            <dt className="text-[11px] uppercase tracking-wide text-neutral-500">
              {t("startOffsetSeconds")}
            </dt>
            <dd className="font-medium text-black dark:text-white">{config.startOffsetSeconds}s</dd>
          </div>
          <div className="flex flex-col">
            <dt className="text-[11px] uppercase tracking-wide text-neutral-500">{t("enabled")}</dt>
            <dd className="font-medium text-black dark:text-white">
              {config.enabled ? tCommon("yes") : tCommon("no")}
            </dd>
          </div>
        </dl>
        <div className="flex justify-end gap-2 pt-2">
          <Button
            type="button"
            variant="destructive"
            size="sm"
            onClick={() => remove({ songId })}
            disabled={removePending}
          >
            {t("removeButton")}
          </Button>
          <Button type="button" size="sm" onClick={() => setShowForm(true)}>
            {tActions("edit")}
          </Button>
        </div>
      </div>
    );
  }

  return (
    <form
      onSubmit={onSubmit}
      className="flex flex-col gap-4 rounded-xl border border-neutral-200 bg-white p-4 dark:border-neutral-800 dark:bg-black"
    >
      <h3 className="text-sm font-semibold text-black dark:text-white">{t("title")}</h3>

      <div className="flex flex-col gap-2">
        <Label htmlFor="config-voice-tag">{t("selectVoiceTag")}</Label>
        <select
          id="config-voice-tag"
          {...register("voiceTagId")}
          disabled={tagsLoading}
          className="h-8 w-full rounded-lg border border-input bg-transparent px-2.5 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30"
        >
          <option value="" disabled>
            {t("selectVoiceTagPlaceholder")}
          </option>
          {tags.map((tag) => (
            <option key={tag.id} value={tag.id}>
              {tag.name}
            </option>
          ))}
        </select>
        {errors.voiceTagId && (
          <p className="text-xs text-red-600 dark:text-red-400">
            {resolveValidationError(errors.voiceTagId.message, {}, tValidation)}
          </p>
        )}
      </div>

      <div className="flex flex-col gap-1">
        <Label htmlFor="config-interval">
          {t("intervalSeconds")}: {interval}s
        </Label>
        <input
          id="config-interval"
          type="range"
          min={1}
          max={60}
          step={1}
          {...register("intervalSeconds", { valueAsNumber: true })}
        />
        {errors.intervalSeconds && (
          <p className="text-xs text-red-600 dark:text-red-400">
            {resolveValidationError(
              errors.intervalSeconds.message,
              { max: 60 },
              tValidation,
            )}
          </p>
        )}
      </div>

      <div className="flex flex-col gap-1">
        <Label htmlFor="config-volume">
          {t("volumePercentage")}: {volume}%
        </Label>
        <input
          id="config-volume"
          type="range"
          min={0}
          max={100}
          step={1}
          {...register("volumePercentage", { valueAsNumber: true })}
        />
        {errors.volumePercentage && (
          <p className="text-xs text-red-600 dark:text-red-400">
            {resolveValidationError(
              errors.volumePercentage.message,
              { max: 100 },
              tValidation,
            )}
          </p>
        )}
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <div className="flex flex-col gap-2">
          <Label htmlFor="config-fade-in">{t("fadeInMs")}</Label>
          <Input
            id="config-fade-in"
            type="number"
            min={0}
            max={5000}
            {...register("fadeInDurationMs", { valueAsNumber: true })}
            onChange={(e) => setValue("fadeInDurationMs", Number(e.target.value))}
          />
          {errors.fadeInDurationMs && (
            <p className="text-xs text-red-600 dark:text-red-400">
              {resolveValidationError(
                errors.fadeInDurationMs.message,
                { max: 5000 },
                tValidation,
              )}
            </p>
          )}
        </div>
        <div className="flex flex-col gap-2">
          <Label htmlFor="config-fade-out">{t("fadeOutMs")}</Label>
          <Input
            id="config-fade-out"
            type="number"
            min={0}
            max={5000}
            {...register("fadeOutDurationMs", { valueAsNumber: true })}
            onChange={(e) => setValue("fadeOutDurationMs", Number(e.target.value))}
          />
          {errors.fadeOutDurationMs && (
            <p className="text-xs text-red-600 dark:text-red-400">
              {resolveValidationError(
                errors.fadeOutDurationMs.message,
                { max: 5000 },
                tValidation,
              )}
            </p>
          )}
        </div>
      </div>

      <div className="flex flex-col gap-2">
        <Label htmlFor="config-start-offset">{t("startOffsetSeconds")}</Label>
        <Input
          id="config-start-offset"
          type="number"
          min={0}
          max={300}
          {...register("startOffsetSeconds", { valueAsNumber: true })}
          onChange={(e) => setValue("startOffsetSeconds", Number(e.target.value))}
        />
        {errors.startOffsetSeconds && (
          <p className="text-xs text-red-600 dark:text-red-400">
            {resolveValidationError(
              errors.startOffsetSeconds.message,
              { max: 300 },
              tValidation,
            )}
          </p>
        )}
      </div>

      <label className="flex items-center gap-2 text-sm">
        <input
          type="checkbox"
          checked={enabled}
          onChange={(e) => setValue("enabled", e.target.checked)}
        />
        {t("enabled")}
      </label>

      <div className="flex justify-end gap-2">
        {config && (
          <Button
            type="button"
            variant="outline"
            onClick={() => {
              reset(getDefaultValues(config));
              setShowForm(false);
            }}
          >
            {tActions("cancel")}
          </Button>
        )}
        <Button type="submit" disabled={isPending || !voiceTagId}>
          {t("saveButton")}
        </Button>
      </div>
    </form>
  );
}
