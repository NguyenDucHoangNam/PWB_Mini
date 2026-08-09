"use client";

import { useTranslations } from "next-intl";
import { CheckCircle2 } from "lucide-react";
import { NEU_TEXT, NEU_TEXT_MUTED, NeuButton } from "@/components/ui/neu";
import { useAutoJoinCountdown } from "../../hooks/use-auto-join-countdown";

interface AutoJoinCountdownProps {
  onEnter: () => void;
}

export function AutoJoinCountdown({ onEnter }: AutoJoinCountdownProps) {
  const t = useTranslations("liveroom.lobby");
  const { remaining, cancelled, cancel } = useAutoJoinCountdown(onEnter);

  return (
    <div className="neu-pressed flex min-h-0 flex-1 flex-col items-center justify-center gap-5 rounded-3xl border-none p-6 text-center">
      <div className="neu-raised grid size-16 place-items-center rounded-full border-none text-indigo-600 dark:text-indigo-400">
        <CheckCircle2 className="size-7" aria-hidden />
      </div>
      <div className="flex flex-col gap-1.5">
        <p className={`text-lg font-bold tracking-tight ${NEU_TEXT}`}>{t("approved")}</p>
        <p aria-live="polite" className={`text-sm font-medium ${NEU_TEXT_MUTED}`}>
          {cancelled ? t("waitingHint") : t("autoJoinIn", { seconds: remaining })}
        </p>
      </div>
      <div className="flex w-full flex-col gap-3 sm:w-auto sm:flex-row">
        <NeuButton variant="primary" onClick={onEnter}>
          {t("enterNow")}
        </NeuButton>
        {!cancelled ? <NeuButton onClick={cancel}>{t("stayHere")}</NeuButton> : null}
      </div>
    </div>
  );
}