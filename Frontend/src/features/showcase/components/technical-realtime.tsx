"use client";

import { Radio } from "lucide-react";
import { useTranslations } from "next-intl";
import { REALTIME_TOPIC } from "@/features/showcase/lib/technical-topics";
import { TechnicalTopic } from "./technical-topic";
import { TechnicalSectionShell } from "./technical-shell";

export function TechnicalRealtime() {
  const t = useTranslations("features.technical");

  return (
    <TechnicalSectionShell
      id="realtime"
      index={3}
      icon={Radio}
      eyebrow={t("sections.realtime.eyebrow")}
      title={t("sections.realtime.title")}
    >
      <div className="mt-10">
        <TechnicalTopic spec={REALTIME_TOPIC} />
      </div>
    </TechnicalSectionShell>
  );
}
