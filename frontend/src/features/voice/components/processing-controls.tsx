"use client";

import { useEffect } from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { asApiError } from "@/lib/api-client";
import { resolveErrorI18nKey } from "@/lib/error-code-to-i18n";
import { useTriggerProcessing } from "../api/song-processing";
import { useProcessingPolling } from "../hooks/use-processing-polling";
import type { SongStatus } from "../types";

interface ProcessingControlsProps {
  songId: string;
  status: SongStatus;
  hasConfig: boolean;
}

export function ProcessingControls({ songId, status, hasConfig }: ProcessingControlsProps) {
  const t = useTranslations("voice.processing");
  const tErrors = useTranslations("voice.errors");
  const tCommon = useTranslations("common");

  const polling = useProcessingPolling({
    songId,
    enabled: status === "PROCESSING",
  });

  useEffect(() => {
    if (!polling.data) return;
    const newStatus = polling.data.data?.status;
    if (newStatus === "PROCESSED") {
      toast.success(t("successToast"));
    } else if (newStatus === "FAILED") {
      toast.error(t("failedToast"));
    }
  }, [polling.data, t]);

  const { mutate: trigger, isPending } = useTriggerProcessing({
    mutationConfig: {
      onSuccess: (response) => {
        if (response.success) {
          toast.info(t("queued"));
        }
      },
      onError: asApiError((err) => {
        const key = resolveErrorI18nKey(err);
        if (key === "voice.errors.noConfigToast" || err.code === "VOICE_001") {
          toast.error(t("noConfigToast"));
          return;
        }
        toast.error(key ? tErrors(key.split(".").pop() as never) : tCommon("error"));
      }),
    },
  });

  if (status === "PROCESSING") {
    return (
      <div
        role="status"
        aria-live="polite"
        className="flex flex-col gap-2 rounded-xl border border-yellow-300 bg-yellow-50 p-4 dark:border-yellow-800 dark:bg-yellow-950/30"
      >
        <div className="flex items-center gap-2 text-sm font-medium text-yellow-800 dark:text-yellow-300">
          <Spinner size="sm" />
          {t("queued")}
        </div>
        <p className="text-xs text-yellow-700 dark:text-yellow-400">{t("pollingHint")}</p>
        {polling.isTimedOut && (
          <p className="text-xs text-yellow-700 dark:text-yellow-400">
            {t("backgroundProcessing")}
          </p>
        )}
        <Button
          size="sm"
          variant="outline"
          onClick={() => polling.refetch()}
          disabled={polling.isFetching}
          aria-label={t("manualRefresh")}
        >
          {t("manualRefresh")}
        </Button>
      </div>
    );
  }

  if (status === "FAILED") {
    return (
      <Button onClick={() => trigger({ songId })} disabled={isPending || !hasConfig}>
        {t("retry")}
      </Button>
    );
  }

  if (status === "PROCESSED") {
    return (
      <Button
        variant="outline"
        onClick={() => trigger({ songId })}
        disabled={isPending || !hasConfig}
      >
        {t("retrigger")}
      </Button>
    );
  }

  return (
    <div className="flex flex-col gap-2">
      <Button onClick={() => trigger({ songId })} disabled={isPending || !hasConfig}>
        {t("trigger")}
      </Button>
      {!hasConfig && (
        <p className="text-xs text-neutral-500 dark:text-neutral-400">{t("noConfigToast")}</p>
      )}
    </div>
  );
}