"use client";

import { useTranslations } from "next-intl";
import { NEU_LABEL } from "@/components/ui/neu";

export const JOIN_TOTAL_STEPS = 4;

export function JoinStepIndicator({ current }: { current: number }) {
  const t = useTranslations("liveroom.join");

  return (
    <div className="flex flex-col gap-2.5">
      <p className={NEU_LABEL}>{t("step", { current, total: JOIN_TOTAL_STEPS })}</p>
      <div
        className="neu-pressed-sm flex gap-1.5 rounded-full border-none p-1"
        role="progressbar"
        aria-valuemin={1}
        aria-valuemax={JOIN_TOTAL_STEPS}
        aria-valuenow={current}
      >
        {Array.from({ length: JOIN_TOTAL_STEPS }, (_, index) => (
          <span
            key={index}
            className={`h-1.5 flex-1 rounded-full beat-16th transition-colors ease-hammer motion-reduce:transition-none ${
              index < current
                ? "bg-indigo-600 dark:bg-indigo-400"
                : "bg-slate-400/40 dark:bg-slate-500/40"
            }`}
          />
        ))}
      </div>
    </div>
  );
}