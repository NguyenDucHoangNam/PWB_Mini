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
      className="flex items-start gap-3 rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm dark:border-amber-800 dark:bg-amber-950/30"
    >
      <AlertTriangle className="mt-0.5 size-4 shrink-0 text-amber-700 dark:text-amber-400" aria-hidden />
      <div className="min-w-0">
        <p className="font-medium text-amber-900 dark:text-amber-200">{t(TITLE_KEY[kind])}</p>
        {kind === "denied" ? (
          <p className="mt-1 text-amber-800 dark:text-amber-300">{t("permissionDeniedHint")}</p>
        ) : null}
      </div>
    </div>
  );
}