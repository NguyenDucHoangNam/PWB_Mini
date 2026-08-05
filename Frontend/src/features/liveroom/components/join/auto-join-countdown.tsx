"use client";

import { useTranslations } from "next-intl";
import { CheckCircle2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useAutoJoinCountdown } from "../../hooks/use-auto-join-countdown";

interface AutoJoinCountdownProps {
  onEnter: () => void;
}

export function AutoJoinCountdown({ onEnter }: AutoJoinCountdownProps) {
  const t = useTranslations("liveroom.lobby");
  const { remaining, cancelled, cancel } = useAutoJoinCountdown(onEnter);

  return (
    <div className="flex flex-col items-center gap-4 rounded-xl border border-green-300 bg-green-50 p-6 text-center dark:border-green-800 dark:bg-green-950/30">
      <CheckCircle2 className="size-8 text-green-700 dark:text-green-400" aria-hidden />
      <p className="text-lg font-semibold text-green-900 dark:text-green-200">
        {t("approved")}
      </p>
      <p aria-live="polite" className="text-sm text-green-800 dark:text-green-300">
        {cancelled ? t("waitingHint") : t("autoJoinIn", { seconds: remaining })}
      </p>
      <div className="flex w-full flex-col gap-2 sm:w-auto sm:flex-row">
        <Button className="h-11 md:h-9" onClick={onEnter}>
          {t("enterNow")}
        </Button>
        {!cancelled ? (
          <Button variant="outline" className="h-11 md:h-9" onClick={cancel}>
            {t("stayHere")}
          </Button>
        ) : null}
      </div>
    </div>
  );
}