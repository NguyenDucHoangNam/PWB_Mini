"use client";

import { useTranslations } from "next-intl";
import { XCircle } from "lucide-react";
import { Button } from "@/components/ui/button";

interface RejectedCardProps {
  reason: string;
  onAskAgain: () => void;
  onBack: () => void;
}

export function RejectedCard({ reason, onAskAgain, onBack }: RejectedCardProps) {
  const t = useTranslations("liveroom.rejected");
  return (
    <div className="flex flex-col items-center gap-4 rounded-xl border border-neutral-200 bg-white p-8 text-center dark:border-neutral-800 dark:bg-black">
      <div className="flex size-14 items-center justify-center rounded-full bg-red-50 text-red-700 dark:bg-red-950/40 dark:text-red-300">
        <XCircle className="size-7" />
      </div>
      <div className="flex flex-col gap-1">
        <h2 className="text-lg font-semibold text-black dark:text-white">
          {t("title")}
        </h2>
        <p className="text-sm text-neutral-500 dark:text-neutral-400">
          {t("subtitle")}
        </p>
      </div>
      {reason && (
        <div className="flex w-full max-w-sm flex-col gap-1 rounded-lg border border-neutral-200 bg-neutral-50 px-4 py-3 text-left dark:border-neutral-800 dark:bg-neutral-900/40">
          <span className="text-xs font-semibold uppercase tracking-wide text-neutral-500 dark:text-neutral-400">
            {t("reasonLabel")}
          </span>
          <span className="text-sm text-black dark:text-white">{reason}</span>
        </div>
      )}
      <div className="flex flex-wrap justify-center gap-2">
        <Button variant="outline" onClick={onBack}>
          {t("backBtn")}
        </Button>
        <Button onClick={onAskAgain}>{t("askAgainBtn")}</Button>
      </div>
    </div>
  );
}
