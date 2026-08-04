"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Loader2, Settings2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Spinner } from "@/components/ui/spinner";
import { asApiError } from "@/lib/api-client";
import { useConfigureVoiceTag, useVoiceTagConfig } from "../api/songs";
import { useListVoiceTags } from "../api/voice-tags";
import { resolveVoiceErrorMessage } from "../lib/resolve-voice-error-message";
import type { ConfigureVoiceTagRequest } from "../types";

const DEFAULTS = {
  intervalSeconds: 30,
  volumePercentage: 80,
  duckingPercentage: 50,
  startOffsetSeconds: 0,
} as const;

interface SongVoiceTagConfigProps {
  songId: string;
  onSaved?: () => void;
}

interface SliderFieldProps {
  id: string;
  label: string;
  hint: string;
  unit: string;
  min: number;
  max: number;
  value: number;
  disabled: boolean;
  onChange: (value: number) => void;
}

function SliderField({
  id,
  label,
  hint,
  unit,
  min,
  max,
  value,
  disabled,
  onChange,
}: SliderFieldProps) {
  return (
    <div className="flex flex-col gap-2 rounded-xl border border-neutral-200 bg-white p-4 dark:border-neutral-800 dark:bg-neutral-950">
      <div className="flex items-center justify-between">
        <Label htmlFor={id} className="text-xs font-medium">
          {label}
        </Label>
        <div className="flex items-center gap-1">
          <Input
            id={id}
            type="number"
            min={min}
            max={max}
            value={value}
            disabled={disabled}
            onChange={(e) => onChange(Number(e.target.value))}
            className="h-7 w-16 px-1.5 text-right text-xs font-semibold"
          />
          <span className="text-xs font-medium text-neutral-500">{unit}</span>
        </div>
      </div>
      <input
        type="range"
        aria-label={label}
        min={min}
        max={max}
        value={value}
        disabled={disabled}
        onChange={(e) => onChange(Number(e.target.value))}
        className="h-1.5 w-full cursor-pointer rounded-lg bg-neutral-200 accent-black dark:bg-neutral-800 dark:accent-white"
      />
      <p className="text-[11px] leading-tight text-neutral-500 dark:text-neutral-400">{hint}</p>
    </div>
  );
}

export function SongVoiceTagConfig({ songId, onSaved }: SongVoiceTagConfigProps) {
  const t = useTranslations("voice.config");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("voice.errors");

  const { data: configRes, isLoading } = useVoiceTagConfig({ songId });
  const { data: voiceTagsRes } = useListVoiceTags({ page: 0, size: 50 });
  const voiceTags = voiceTagsRes?.data?.content ?? [];

  const saved = configRes?.data ?? null;

  const [draft, setDraft] = useState<ConfigureVoiceTagRequest | null>(null);
  const [syncedConfigId, setSyncedConfigId] = useState<string | null>(null);
  const savedKey = saved?.id ?? null;
  if (!isLoading && syncedConfigId !== savedKey) {
    setSyncedConfigId(savedKey);
    setDraft(
      saved
        ? {
            voiceTagId: saved.voiceTagId,
            intervalSeconds: saved.intervalSeconds,
            volumePercentage: saved.volumePercentage,
            duckingPercentage: saved.duckingPercentage,
            startOffsetSeconds: saved.startOffsetSeconds,
            enabled: saved.enabled,
          }
        : { voiceTagId: "", ...DEFAULTS, enabled: true },
    );
  }

  const { mutate: configure, isPending } = useConfigureVoiceTag({
    mutationConfig: {
      onSuccess: (response) => {
        if (response.success) {
          toast.success(t("saveSuccess"));
          onSaved?.();
        } else {
          toast.error(response.message || tCommon("error"));
        }
      },
      onError: asApiError((err) => {
        toast.error(resolveVoiceErrorMessage(err, tErrors, tCommon));
      }),
    },
  });

  if (isLoading || !draft) {
    return (
      <div className="flex items-center justify-center gap-3 rounded-xl border border-neutral-200 p-8 text-sm text-neutral-500 dark:border-neutral-800">
        <Spinner size="sm" />
      </div>
    );
  }

  const update = <K extends keyof ConfigureVoiceTagRequest>(
    key: K,
    value: ConfigureVoiceTagRequest[K],
  ) => setDraft((prev) => (prev ? { ...prev, [key]: value } : prev));

  const handleSave = () => {
    if (!draft.voiceTagId) {
      toast.error(t("selectVoiceTagPlaceholder"));
      return;
    }
    configure({ songId, data: draft });
  };

  return (
    <section className="flex flex-col gap-4 rounded-xl border border-neutral-200 bg-white p-5 dark:border-neutral-800 dark:bg-black">
      <div className="flex items-center gap-2.5">
        <Settings2 className="size-4 text-neutral-500" aria-hidden="true" />
        <div className="flex flex-col">
          <h2 className="text-sm font-semibold text-neutral-900 dark:text-neutral-100">
            {t("title")}
          </h2>
          {!saved && (
            <span className="text-xs text-neutral-500 dark:text-neutral-400">{t("noConfig")}</span>
          )}
        </div>
      </div>

      {voiceTags.length === 0 ? (
        <p className="rounded-lg border border-neutral-200 bg-neutral-50 p-3 text-xs text-neutral-600 dark:border-neutral-800 dark:bg-neutral-900 dark:text-neutral-400">
          {t("noVoiceTags")}
        </p>
      ) : (
        <>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="config-voice-tag" className="text-xs font-medium">
              {t("selectVoiceTag")}
            </Label>
            <select
              id="config-voice-tag"
              value={draft.voiceTagId}
              disabled={isPending}
              onChange={(e) => update("voiceTagId", e.target.value)}
              className="h-9 w-full rounded-lg border border-input bg-transparent px-2.5 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-neutral-950"
            >
              <option value="">{t("selectVoiceTagPlaceholder")}</option>
              {voiceTags.map((tag) => (
                <option key={tag.id} value={tag.id}>
                  {tag.name}
                </option>
              ))}
            </select>
          </div>

          <div className="grid gap-4 md:grid-cols-2">
            <SliderField
              id="config-interval"
              label={t("intervalSeconds")}
              hint={t("intervalHint")}
              unit="s"
              min={5}
              max={600}
              value={draft.intervalSeconds}
              disabled={isPending}
              onChange={(v) => update("intervalSeconds", v)}
            />
            <SliderField
              id="config-volume"
              label={t("volumePercentage")}
              hint={t("volumeHint")}
              unit="%"
              min={0}
              max={100}
              value={draft.volumePercentage}
              disabled={isPending}
              onChange={(v) => update("volumePercentage", v)}
            />
            <SliderField
              id="config-ducking"
              label={t("duckingPercentage")}
              hint={t("duckingHint")}
              unit="%"
              min={0}
              max={100}
              value={draft.duckingPercentage}
              disabled={isPending}
              onChange={(v) => update("duckingPercentage", v)}
            />
            <SliderField
              id="config-offset"
              label={t("startOffsetSeconds")}
              hint={t("startOffsetHint")}
              unit="s"
              min={0}
              max={120}
              value={draft.startOffsetSeconds}
              disabled={isPending}
              onChange={(v) => update("startOffsetSeconds", v)}
            />
          </div>

          <label className="flex cursor-pointer select-none items-center gap-2.5 text-sm text-neutral-800 dark:text-neutral-200">
            <input
              type="checkbox"
              checked={draft.enabled ?? true}
              disabled={isPending}
              onChange={(e) => update("enabled", e.target.checked)}
              className="size-4 rounded border-input accent-black dark:accent-white"
            />
            {t("enabled")}
          </label>

          <div className="flex items-center justify-between gap-3 border-t border-neutral-200 pt-4 dark:border-neutral-800">
            <p className="text-xs text-neutral-500 dark:text-neutral-400">{t("saveHint")}</p>
            <Button onClick={handleSave} disabled={isPending} className="shrink-0">
              {isPending && <Loader2 className="mr-2 size-4 animate-spin" aria-hidden="true" />}
              {t("saveButton")}
            </Button>
          </div>
        </>
      )}
    </section>
  );
}
