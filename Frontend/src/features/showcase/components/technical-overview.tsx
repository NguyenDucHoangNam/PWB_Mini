"use client";

import { Layers } from "lucide-react";
import { useTranslations } from "next-intl";
import { Reveal } from "@/components/marketing/section-primitives";
import { TechnicalPlaceholder, TechnicalSectionShell } from "./technical-shell";

export function TechnicalOverview() {
  const t = useTranslations("features.technical");

  return (
    <TechnicalSectionShell
      id="overview"
      index={1}
      icon={Layers}
      eyebrow={t("sections.overview.eyebrow")}
      title={t("sections.overview.title")}
    >
      <Reveal>
        <TechnicalPlaceholder className="mt-10 min-h-[26rem]" />
      </Reveal>
    </TechnicalSectionShell>
  );
}
