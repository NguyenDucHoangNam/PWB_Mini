"use client";

import { useTranslations } from "next-intl";
import type { RoomStatus } from "../../types";

const STATUS_STYLES: Record<RoomStatus, string> = {
  ACTIVE: "border-border bg-secondary text-secondary-foreground",
  ENDED: "border-dashed border-border bg-muted/50 text-muted-foreground",
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
        <span className="flex h-3 w-3 shrink-0 items-end gap-0.5" aria-hidden="true">
          <span className="h-full w-0.5 rounded-full bg-foreground animate-[bounce_1s_infinite_100ms]" />
          <span className="h-2/3 w-0.5 rounded-full bg-foreground animate-[bounce_1s_infinite_300ms]" />
          <span className="h-4/5 w-0.5 rounded-full bg-foreground animate-[bounce_1s_infinite_200ms]" />
        </span>
      ) : (
        <span className="size-1.5 rounded-full bg-muted-foreground/60" aria-hidden="true" />
      )}
      {t(STATUS_LABEL_KEY[status])}
    </span>
  );
}