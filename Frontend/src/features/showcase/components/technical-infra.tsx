"use client";

import { Server } from "lucide-react";
import { useTranslations } from "next-intl";
import { Reveal } from "@/components/marketing/section-primitives";
import {
  DEPLOYMENT_DIAGRAM,
  PIPELINE_DIAGRAM,
  SECURITY_DIAGRAM,
} from "@/features/showcase/lib/technical-diagrams";
import { TechnicalDiagram } from "./technical-diagram";
import { TechnicalOutbox } from "./technical-outbox";
import { TechnicalRedis } from "./technical-redis";
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
      <Reveal className="mt-10">
        <TechnicalDiagram spec={DEPLOYMENT_DIAGRAM} />
      </Reveal>

      <Reveal className="mt-8">
        <TechnicalDiagram spec={SECURITY_DIAGRAM} />
      </Reveal>

      <Reveal className="mt-8">
        <TechnicalDiagram spec={PIPELINE_DIAGRAM} />
      </Reveal>

      <div className="mt-8">
        <TechnicalOutbox />
      </div>

      <div className="mt-8">
        <TechnicalRedis />
      </div>
    </TechnicalSectionShell>
  );
}

