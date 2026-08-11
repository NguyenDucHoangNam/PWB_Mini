"use client";

import { Server } from "lucide-react";
import { useTranslations } from "next-intl";
import { Reveal } from "@/components/marketing/section-primitives";
import { DEPLOYMENT_DIAGRAM, PIPELINE_DIAGRAM } from "@/features/showcase/lib/technical-diagrams";
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
      {/* Where it runs, then how it gets there. Both are infrastructure questions and neither
          belongs to a single module, which is why they land in this panel rather than above. */}
      <Reveal className="mt-10">
        <TechnicalDiagram spec={DEPLOYMENT_DIAGRAM} />
      </Reveal>

      <Reveal className="mt-8">
        <TechnicalDiagram spec={PIPELINE_DIAGRAM} />
      </Reveal>

      {/* The event queue goes first and gets its own chapter format rather than the shared
          four-question card. It carries the same weight as the realtime layer — everything
          asynchronous in the system runs through it — and the question it answers is a "why
          does this exist at all", which the card format cannot hold. */}
      <div className="mt-8">
        <TechnicalOutbox />
      </div>

      {/* Redis follows for the same reason and in the same format. It is the other piece every
          module touches, and "an in-memory key-value store" means nothing until the reader has
          seen the data it holds — which is an argument, not four answers in four boxes. */}
      <div className="mt-8">
        <TechnicalRedis />
      </div>
    </TechnicalSectionShell>
  );
}
