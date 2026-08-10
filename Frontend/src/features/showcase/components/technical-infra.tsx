"use client";

import { Server } from "lucide-react";
import { useTranslations } from "next-intl";
import { Reveal, RevealGroup, RevealItem } from "@/components/marketing/section-primitives";
import { INFRA_CARDS } from "@/features/showcase/lib/technical-sections";
import { DEPLOYMENT_DIAGRAM, PIPELINE_DIAGRAM } from "@/features/showcase/lib/technical-diagrams";
import { INFRA_TOPICS } from "@/features/showcase/lib/technical-topics";
import { TechnicalDiagram } from "./technical-diagram";
import { TechnicalTopic } from "./technical-topic";
import { TechnicalPlaceholder, TechnicalSectionShell } from "./technical-shell";

/* Three of the six infrastructure topics are written up in full below, so their placeholder
   cards would be duplicates. The rest keep theirs. */
const WRITTEN_UP = new Set(INFRA_TOPICS.map((topic) => topic.id));

export function TechnicalInfra() {
  const t = useTranslations("features.technical");

  return (
    <TechnicalSectionShell
      id="infrastructure"
      index={4}
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

      <div className="mt-8 flex flex-col gap-8">
        {INFRA_TOPICS.map((topic) => (
          <TechnicalTopic key={topic.id} spec={topic} />
        ))}
      </div>

      <RevealGroup className="mt-8 grid gap-6 sm:grid-cols-2 xl:grid-cols-3">
        {INFRA_CARDS.filter(({ key }) => !WRITTEN_UP.has(key)).map(({ key, icon: Icon }) => (
          <RevealItem key={key} className="h-full">
            <article className="neu-lift flex h-full flex-col rounded-3xl bg-[#e0e5ec] p-6 dark:bg-[#1e222b]">
              <div className="flex items-center gap-3.5">
                <span
                  aria-hidden="true"
                  className="neu-pressed flex size-10 shrink-0 items-center justify-center rounded-xl bg-[#e0e5ec] text-indigo-600 dark:bg-[#1e222b] dark:text-indigo-400"
                >
                  <Icon className="size-4" />
                </span>
                <h3 className="font-heading text-base font-bold tracking-tight text-slate-900 dark:text-slate-50 sm:text-lg">
                  {t(`cards.infra.${key}`)}
                </h3>
              </div>

              <TechnicalPlaceholder className="mt-5 min-h-[10rem] grow" />
            </article>
          </RevealItem>
        ))}
      </RevealGroup>
    </TechnicalSectionShell>
  );
}
