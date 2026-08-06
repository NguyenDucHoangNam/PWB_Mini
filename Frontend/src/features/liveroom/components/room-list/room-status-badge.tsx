"use client";

import { useTranslations } from "next-intl";
import type { RoomStatus } from "../../types";

const STATUS_STYLES: Record<RoomStatus, string> = {
  ACTIVE:
    "border-neutral-300 bg-neutral-100 text-neutral-900 dark:border-neutral-700 dark:bg-neutral-900 dark:text-neutral-100 font-mono",
  ENDED:
    "border-dashed border-neutral-400 bg-neutral-50 text-neutral-600 dark:border-neutral-700 dark:bg-neutral-950 dark:text-neutral-400 font-mono",
};

const STATUS_LABEL_KEY: Record<RoomStatus, string> = {
  ACTIVE: "tabActive",
  ENDED: "tabEnded",
};

export function RoomStatusBadge({
  status,
  className = "",
}: {
  status: RoomStatus;
  className?: string;
}) {
  const t = useTranslations("liveroom.list");

  return (
    <span
      className={`inline-flex shrink-0 items-center gap-1.5 rounded-md border px-2 py-0.5 text-[11px] font-semibold tracking-wide ${STATUS_STYLES[status]} ${className}`}
    >
      {status === "ACTIVE" ? (
        <span className="flex items-end gap-0.5 h-3 w-3 shrink-0" aria-hidden="true">
          <span className="w-0.5 bg-black dark:bg-white animate-[bounce_1s_infinite_100ms] h-full rounded-full" />
          <span className="w-0.5 bg-black dark:bg-white animate-[bounce_1s_infinite_300ms] h-2/3 rounded-full" />
          <span className="w-0.5 bg-black dark:bg-white animate-[bounce_1s_infinite_200ms] h-4/5 rounded-full" />
        </span>
      ) : (
        <span className="size-1.5 rounded-full bg-neutral-400 dark:bg-neutral-600" aria-hidden="true" />
      )}
      {t(STATUS_LABEL_KEY[status])}
    </span>
  );
}