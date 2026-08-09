"use client";

import { useTranslations } from "next-intl";
import { NeuBadge } from "@/components/ui/neu";
import type { RoomStatus } from "../../types";

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

  if (status === "ACTIVE") {
    return (
      <NeuBadge tone="accent" className={className}>
        <span className="flex h-3 w-3 shrink-0 items-end gap-0.5" aria-hidden="true">
          <span className="h-full w-0.5 animate-[bounce_1s_infinite_100ms] rounded-full bg-current motion-reduce:animate-none" />
          <span className="h-2/3 w-0.5 animate-[bounce_1s_infinite_300ms] rounded-full bg-current motion-reduce:animate-none" />
          <span className="h-4/5 w-0.5 animate-[bounce_1s_infinite_200ms] rounded-full bg-current motion-reduce:animate-none" />
        </span>
        {t(STATUS_LABEL_KEY[status])}
      </NeuBadge>
    );
  }

  return (
    <NeuBadge tone="muted" className={className}>
      <span className="size-1.5 rounded-full bg-current opacity-60" aria-hidden="true" />
      {t(STATUS_LABEL_KEY[status])}
    </NeuBadge>
  );
}
