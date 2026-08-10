"use client";

import { Server } from "lucide-react";
import { useTranslations } from "next-intl";
import { Reveal } from "@/components/marketing/section-primitives";
import { DEPLOYMENT_DIAGRAM, PIPELINE_DIAGRAM } from "@/features/showcase/lib/technical-diagrams";
import { INFRA_TOPICS } from "@/features/showcase/lib/technical-topics";
import { TechnicalDiagram } from "./technical-diagram";
import { TechnicalTopic } from "./technical-topic";
import { TechnicalSectionShell } from "./technical-shell";

export function TechnicalInfra() {
  const t = useTranslations("features.technical");

  return (
    <TechnicalSectionShell
      id="infrastructure"
      icon={Server}
      eyebrow={t("sections.infrastructure.eyebrow")}
      title={t("sections.infrastructure.title")}
    >
      {/* Where it runs, then how it gets there. Both are infrastructure questions and neither
          belongs to a single module, which is why they land in this panel rather than above. */}
      <Reveal className="mt-10">
        <TechnicalDiagram spec={DEPLOYMENT_DIAGRAM} />
      </Reveal>

      <Reveal className="mt-8">
        <TechnicalDiagram spec={PIPELINE_DIAGRAM} />
      </Reveal>

      {/* All six infrastructure topics are written up now, so the placeholder card grid that
          used to sit here is gone rather than left rendering an empty row. */}
      <div className="mt-8 flex flex-col gap-8">
        {INFRA_TOPICS.map((topic) => (
          <TechnicalTopic key={topic.id} spec={topic} />
        ))}
      </div>
    </TechnicalSectionShell>
  );
}
