"use client";

import { Layers } from "lucide-react";
import { useTranslations } from "next-intl";
import { Reveal } from "@/components/marketing/section-primitives";
import { CONTAINER_DIAGRAM, CONTEXT_DIAGRAM } from "@/features/showcase/lib/technical-diagrams";
import { TechnicalDiagram } from "./technical-diagram";
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
      {/* Context then containers: the same system twice, once from outside and once with the
          lid off. They belong in one panel because the second only makes sense after the first. */}
      <Reveal className="mt-10">
        <TechnicalDiagram spec={CONTEXT_DIAGRAM} />
      </Reveal>

      <Reveal className="mt-8">
        <TechnicalDiagram spec={CONTAINER_DIAGRAM} />
      </Reveal>
    </TechnicalSectionShell>
  );
}
