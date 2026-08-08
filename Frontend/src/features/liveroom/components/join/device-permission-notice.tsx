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
      className="flex items-start gap-3 rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive"
    >
      <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden />
      <div className="min-w-0">
        <p className="font-medium">{t(TITLE_KEY[kind])}</p>
        {kind === "denied" ? (
          <p className="mt-1 text-destructive/80">{t("permissionDeniedHint")}</p>
        ) : null}
      </div>
    </div>
  );
}