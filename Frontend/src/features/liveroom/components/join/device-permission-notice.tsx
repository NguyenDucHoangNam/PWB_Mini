"use client";

import { useTranslations } from "next-intl";
import { AlertTriangle } from "lucide-react";
import type { MediaErrorKind } from "../../lib/media-constraints";

const TITLE_KEY: Record<MediaErrorKind, string> = {
  denied: "permissionDenied",
  notFound: "noDevice",
  busy: "deviceBusy",
  overconstrained: "noDevice",
  unsupported: "noDevice",
  unknown: "noDevice",
};

export function DevicePermissionNotice({ kind }: { kind: MediaErrorKind }) {
  const t = useTranslations("liveroom.prejoin");

  return (
    <div
      role="status"
      className="neu-pressed-sm flex items-start gap-3 rounded-2xl border-none p-3.5 text-sm text-rose-700 dark:text-rose-400"
    >
      <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden />
      <div className="min-w-0">
        <p className="font-bold">{t(TITLE_KEY[kind])}</p>
        {kind === "denied" ? (
          <p className="mt-1 font-medium opacity-90">{t("permissionDeniedHint")}</p>
        ) : null}
      </div>
    </div>
  );
}