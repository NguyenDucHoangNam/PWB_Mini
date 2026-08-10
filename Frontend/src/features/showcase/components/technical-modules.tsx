"use client";

import { Workflow } from "lucide-react";
import { useTranslations } from "next-intl";
import { Reveal, RevealGroup, RevealItem } from "@/components/marketing/section-primitives";
import { MODULE_CARDS } from "@/features/showcase/lib/technical-sections";
import { LAYERS_DIAGRAM } from "@/features/showcase/lib/technical-diagrams";
import { TechnicalDiagram } from "./technical-diagram";
import { TechnicalPlaceholder, TechnicalSectionShell } from "./technical-shell";

export function TechnicalModules() {
  const t = useTranslations("features.technical");

  return (
    <TechnicalSectionShell
      id="modules"
      index={2}
      icon={Workflow}
      eyebrow={t("sections.modules.eyebrow")}
      title={t("sections.modules.title")}
    >
      {/* All three modules repeat the same four layers, so the anatomy is drawn once here
          rather than three times below. */}
      <Reveal className="mt-10">
        <TechnicalDiagram spec={LAYERS_DIAGRAM} />
      </Reveal>

      {/* A module carries several docs' worth of material, so each one gets a full-width
          block. The infrastructure topics get a tighter grid — the difference in footprint
          is what tells the reader which weighs more. */}
      <RevealGroup className="mt-8 flex flex-col gap-8">
        {MODULE_CARDS.map(({ key, icon: Icon, stack }) => (
          <RevealItem key={key}>
            <article className="neu-lift rounded-3xl bg-[#e0e5ec] p-6 dark:bg-[#1e222b] sm:p-8">
              <div className="flex flex-wrap items-start justify-between gap-5">
                <div className="flex items-center gap-4">
                  <span
                    aria-hidden="true"
                    className="neu-pressed flex size-12 shrink-0 items-center justify-center rounded-2xl bg-[#e0e5ec] text-indigo-600 dark:bg-[#1e222b] dark:text-indigo-400"
                  >
                    <Icon className="size-5" />
                  </span>
                  <h3 className="font-heading text-xl font-bold tracking-tight text-slate-900 dark:text-slate-50 sm:text-2xl">
                    {t(`cards.modules.${key}`)}
                  </h3>
                </div>

                <ul className="flex flex-wrap gap-2">
                  {stack.map((tech) => (
                    <li
                      key={tech}
                      className="neu-pressed-sm rounded-full bg-[#e0e5ec] px-3 py-1 font-mono text-[0.68rem] font-semibold text-slate-500 dark:bg-[#1e222b] dark:text-slate-400"
                    >
                      {tech}
                    </li>
                  ))}
                </ul>
              </div>

              <TechnicalPlaceholder className="mt-7 min-h-[14rem]" />
            </article>
          </RevealItem>
        ))}
      </RevealGroup>
    </TechnicalSectionShell>
  );
}
