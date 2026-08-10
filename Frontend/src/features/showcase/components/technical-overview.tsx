"use client";

import { Layers } from "lucide-react";
import { useTranslations } from "next-intl";
import { Reveal } from "@/components/marketing/section-primitives";
import {
  CONTAINER_DIAGRAM,
  CONTEXT_DIAGRAM,
  LAYERS_DIAGRAM,
} from "@/features/showcase/lib/technical-diagrams";
import { TechnicalDiagram } from "./technical-diagram";
import { TechnicalSectionShell } from "./technical-shell";

export function TechnicalOverview() {
  const t = useTranslations("features.technical");

  return (
    <TechnicalSectionShell
      id="overview"
      icon={Layers}
      eyebrow={t("sections.overview.eyebrow")}
      title={t("sections.overview.title")}
    >
      <Reveal className="mt-10 flex flex-col gap-8">
        <TechnicalDiagram spec={CONTEXT_DIAGRAM} />
        <TechnicalDiagram spec={CONTAINER_DIAGRAM} />
        <TechnicalDiagram spec={LAYERS_DIAGRAM} />
      </Reveal>
    </TechnicalSectionShell>
  );
}
