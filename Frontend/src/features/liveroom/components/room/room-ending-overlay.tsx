"use client";

import { useEffect, useState } from "react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
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
    <div className="absolute inset-0 z-30 flex items-center justify-center bg-black/70 p-4">
      <div className="flex w-full max-w-sm flex-col items-center gap-4 rounded-xl border border-neutral-200 bg-white p-6 text-center dark:border-neutral-800 dark:bg-black">
        <h2 className="text-lg font-semibold text-black dark:text-white">
          {manual ? t("manualTitle") : t("autoTitle")}
        </h2>
        {endedReason ? (
          <p className="text-sm text-neutral-500 dark:text-neutral-400">
            {t(REASON_KEY[endedReason])}
          </p>
        ) : null}
        <p aria-live="polite" className="text-sm text-neutral-500 dark:text-neutral-400">
          {t("countdown", { seconds: remaining })}
        </p>
        <Button className="h-11 w-full md:h-9" onClick={onLeave}>
          {t("leaveNow")}
        </Button>
      </div>
    </div>
  );
}