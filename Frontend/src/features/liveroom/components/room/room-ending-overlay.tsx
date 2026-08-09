"use client";

import { useEffect, useState } from "react";
import { useTranslations } from "next-intl";
import { NEU_TEXT, NEU_TEXT_MUTED, NeuButton, NeuPanel } from "@/components/ui/neu";
import { useLiveroomStore } from "../../stores/use-liveroom-store";
import type { EndedReason } from "../../types";

const REASON_KEY: Record<EndedReason, string> = {
  MANUAL: "reasonManual",
  OWNER_GRACE_EXPIRED: "reasonGrace",
  EMPTY_TIMEOUT: "reasonEmpty",
  FORCE_ROLE_CHANGE: "reasonRoleChange",
};


export function RoomEndingOverlay({ onLeave }: { onLeave: () => void }) {
  const t = useTranslations("liveroom.room.ended");
  const endingAt = useLiveroomStore((state) => state.lifecycle.endingAt);
  const endedReason = useLiveroomStore((state) => state.room.endedReason);
  const [remaining, setRemaining] = useState(0);

  useEffect(() => {
    if (endingAt === null) return;
    const tick = () => {
      const left = endingAt - Date.now();
      setRemaining(Math.max(0, Math.ceil(left / 1000)));
      if (left <= 0) {
        window.clearInterval(id);
        onLeave();
      }
    };
    const id = window.setInterval(tick, 250);
    tick();
    return () => window.clearInterval(id);
  }, [endingAt, onLeave]);

  if (endingAt === null) return null;

  const manual = endedReason === null || endedReason === "MANUAL";

  return (
    <div className="absolute inset-0 z-30 flex items-center justify-center bg-slate-900/70 p-4">
      <NeuPanel className="flex w-full max-w-sm flex-col items-center gap-4 p-6 text-center">
        <h2 className={`text-lg font-bold ${NEU_TEXT}`}>
          {manual ? t("manualTitle") : t("autoTitle")}
        </h2>
        {endedReason ? (
          <p className={`text-sm font-medium ${NEU_TEXT_MUTED}`}>{t(REASON_KEY[endedReason])}</p>
        ) : null}
        <p aria-live="polite" className={`text-sm font-medium ${NEU_TEXT_MUTED}`}>
          {t("countdown", { seconds: remaining })}
        </p>
        <NeuButton variant="primary" className="w-full" onClick={onLeave}>
          {t("leaveNow")}
        </NeuButton>
      </NeuPanel>
    </div>
  );
}