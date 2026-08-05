"use client";

import { useTranslations } from "next-intl";
import type { RoomStatus } from "../../types";

const STATUS_STYLES: Record<RoomStatus, string> = {
  ACTIVE:
    "border-green-300 bg-green-50 text-green-800 dark:border-green-800 dark:bg-green-950/30 dark:text-green-300",
  ENDED:
    "border-neutral-300 bg-neutral-50 text-neutral-700 dark:border-neutral-700 dark:bg-neutral-900 dark:text-neutral-400",
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
      className={`inline-flex shrink-0 items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold ${STATUS_STYLES[status]} ${className}`}
    >
      {t(STATUS_LABEL_KEY[status])}
    </span>
  );
}