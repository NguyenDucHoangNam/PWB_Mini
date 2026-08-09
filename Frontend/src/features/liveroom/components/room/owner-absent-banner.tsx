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
      className="neu-pressed-sm mx-3 mt-2 flex shrink-0 items-center justify-center gap-2 rounded-2xl border-none px-3.5 py-2.5 text-xs font-semibold text-amber-700 md:text-sm dark:text-amber-400"
    >
      <AlertTriangle className="size-4 shrink-0" aria-hidden />
      <span>{t("banner")}</span>
      {graceExpiresAt ? (
        <span className="font-bold">
          {t("graceRemaining", { time: "" })}
          <CountdownText deadline={graceExpiresAt} className="ml-1 tabular-nums" />
        </span>
      ) : null}
    </div>
  );
}