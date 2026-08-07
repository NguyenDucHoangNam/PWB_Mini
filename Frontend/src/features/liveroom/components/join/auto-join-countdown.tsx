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
    <div className="flex min-h-0 flex-1 flex-col items-center justify-center gap-4 rounded-xl border border-border bg-card p-6 text-center shadow-xs">
      <div className="grid size-12 place-items-center rounded-full border border-border bg-muted text-foreground">
        <CheckCircle2 className="size-6" aria-hidden />
      </div>
      <div className="flex flex-col gap-1">
        <p className="text-lg font-bold tracking-tight text-foreground">{t("approved")}</p>
        <p aria-live="polite" className="text-sm text-muted-foreground">
          {cancelled ? t("waitingHint") : t("autoJoinIn", { seconds: remaining })}
        </p>
      </div>
      <div className="flex w-full flex-col gap-2 sm:w-auto sm:flex-row">
        <Button className="h-11 font-semibold md:h-9" onClick={onEnter}>
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