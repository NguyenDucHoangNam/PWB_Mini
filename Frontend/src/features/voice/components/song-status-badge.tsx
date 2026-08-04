"use client";

import { useTranslations } from "next-intl";
import type { SongStatus } from "../types";

const STATUS_STYLES: Record<SongStatus, string> = {
  UPLOADED:
    "border-blue-300 bg-blue-50 text-blue-800 dark:border-blue-800 dark:bg-blue-950/30 dark:text-blue-300",
  PROCESSING:
    "border-yellow-300 bg-yellow-50 text-yellow-800 dark:border-yellow-800 dark:bg-yellow-950/30 dark:text-yellow-300",
  PROCESSED:
    "border-green-300 bg-green-50 text-green-800 dark:border-green-800 dark:bg-green-950/30 dark:text-green-300",
  FAILED:
    "border-red-300 bg-red-50 text-red-800 dark:border-red-800 dark:bg-red-950/30 dark:text-red-300",
};

const STATUS_LABEL_KEY: Record<SongStatus, string> = {
  UPLOADED: "uploaded",
  PROCESSING: "processing",
  PROCESSED: "processed",
  FAILED: "failed",
};

export function SongStatusBadge({
  status,
  className = "",
}: {
  status: SongStatus;
  className?: string;
}) {
  const t = useTranslations("voice.status");

  return (
    <span
      className={`inline-flex shrink-0 items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold ${STATUS_STYLES[status]} ${className}`}
    >
      {t(STATUS_LABEL_KEY[status])}
    </span>
  );
}
