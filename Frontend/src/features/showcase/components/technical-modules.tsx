"use client";

import { Workflow } from "lucide-react";
import { useTranslations } from "next-intl";
import { LAYERS_DIAGRAM } from "@/features/showcase/lib/technical-diagrams";
import { TechnicalDiagram } from "./technical-diagram";
import { TechnicalSectionShell } from "./technical-shell";

export function TechnicalModules() {
  const t = useTranslations("features.technical");

  return (
    <TechnicalSectionShell
      id="modules"
      icon={Workflow}
      eyebrow={t("sections.modules.eyebrow")}
      title={t("sections.modules.title")}
    >
      <div className="mt-4">
        <TechnicalDiagram spec={LAYERS_DIAGRAM} />
      </div>
    </TechnicalSectionShell>
  );
}
