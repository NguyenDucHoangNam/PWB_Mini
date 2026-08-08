"use client";

import { useTranslations } from "next-intl";
import { AlertTriangle } from "lucide-react";
import { CountdownText } from "../ui/countdown-text";
import { useLiveroomStore } from "../../stores/use-liveroom-store";

export function OwnerAbsentBanner() {
  const t = useTranslations("liveroom.room.ownerAbsent");
  const ownerAbsent = useLiveroomStore((state) => state.room.ownerAbsent);
  const graceExpiresAt = useLiveroomStore((state) => state.room.graceExpiresAt);

  if (!ownerAbsent) return null;

  return (
    <div
      role="status"
      aria-live="polite"
      className="flex shrink-0 items-center justify-center gap-2 bg-amber-50 px-3 py-2 text-xs text-amber-900 md:text-sm dark:bg-amber-950/40 dark:text-amber-200"
    >
      <AlertTriangle className="size-4 shrink-0" aria-hidden />
      <span>{t("banner")}</span>
      {graceExpiresAt ? (
        <span className="font-medium">
          {t("graceRemaining", { time: "" })}
          <CountdownText deadline={graceExpiresAt} className="ml-1 tabular-nums" />
        </span>
      ) : null}
    </div>
  );
}