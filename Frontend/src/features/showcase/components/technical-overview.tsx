"use client";

import { Layers } from "lucide-react";
import { useTranslations } from "next-intl";
import { Reveal } from "@/components/marketing/section-primitives";
import { TechnicalContextDiagram } from "./technical-context-diagram";
import { TechnicalSectionShell } from "./technical-shell";

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
      <Reveal className="mt-10">
        <TechnicalContextDiagram />
      </Reveal>
    </TechnicalSectionShell>
  );
}
