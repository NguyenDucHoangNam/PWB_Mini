"use client";

import { useTranslations } from "next-intl";

export const JOIN_TOTAL_STEPS = 4;

export function JoinStepIndicator({ current }: { current: number }) {
  const t = useTranslations("liveroom.join");

  return (
    <div className="flex flex-col gap-2">
      <p className="text-xs font-medium text-neutral-500 dark:text-neutral-400">
        {t("step", { current, total: JOIN_TOTAL_STEPS })}
      </p>
      <div
        className="flex gap-1.5"
        role="progressbar"
        aria-valuemin={1}
        aria-valuemax={JOIN_TOTAL_STEPS}
        aria-valuenow={current}
      >
        {Array.from({ length: JOIN_TOTAL_STEPS }, (_, index) => (
          <span
            key={index}
            className={`h-1 flex-1 rounded-full ${
              index < current
                ? "bg-black dark:bg-white"
                : "bg-neutral-200 dark:bg-neutral-800"
            }`}
          />
        ))}
      </div>
    </div>
  );
}