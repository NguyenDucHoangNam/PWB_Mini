"use client";

import type { LucideIcon } from "lucide-react";
import { ListMusic, Mic, Radio } from "lucide-react";
import { useTranslations } from "next-intl";
import {
  Groove,
  RevealGroup,
  RevealItem,
  Section,
  SectionHeader,
} from "@/components/marketing/section-primitives";

const PILLARS = [
  { key: "library", icon: ListMusic },
  { key: "voiceTag", icon: Mic },
  { key: "liveRoom", icon: Radio },
] as const;

const FACT_KEYS = ["fact1", "fact2", "fact3"] as const;

export function SectionPillars() {
  const t = useTranslations("landing.pillars");

  return (
    <Section tone="trough">
      <SectionHeader
        eyebrow={t("eyebrow")}
        title={t("title")}
        lead={t("lead")}
        align="center"
      />

      <RevealGroup className="mt-14 grid gap-7 lg:mt-16 lg:grid-cols-3">
        {PILLARS.map(({ key, icon }) => (
          <PillarCard
            key={key}
            icon={icon}
            index={t(`${key}.index`)}
            title={t(`${key}.title`)}
            description={t(`${key}.description`)}
            facts={FACT_KEYS.map((factKey) => t(`${key}.${factKey}`))}
          />
        ))}
      </RevealGroup>
    </Section>
  );
}

interface PillarCardProps {
  icon: LucideIcon;
  index: string;
  title: string;
  description: string;
  facts: string[];
}

function PillarCard({ icon: Icon, index, title, description, facts }: PillarCardProps) {
  return (
    <RevealItem className="h-full">
      <article className="neu-lift flex h-full flex-col rounded-3xl border-none p-7 sm:p-8">
        <div className="flex items-start justify-between">
          <span className="neu-pressed flex size-14 items-center justify-center rounded-2xl text-indigo-600 dark:text-indigo-400">
            <Icon className="size-6" aria-hidden="true" />
          </span>
          <span className="font-mono text-3xl font-bold leading-none text-slate-400/40 dark:text-slate-600/40">
            {index}
          </span>
        </div>

        <h3 className="mt-7 text-xl font-bold tracking-tight text-slate-900 dark:text-slate-100">{title}</h3>
        <p className="mt-2.5 text-sm leading-relaxed text-slate-600 dark:text-slate-300">{description}</p>

        <Groove className="mt-7" />

        <ul className="mt-1">
          {facts.map((fact, factIndex) => (
            <li key={fact}>
              {factIndex > 0 && <Groove />}
              <span className="flex items-center gap-3 py-3.5 font-mono text-xs text-slate-600 dark:text-slate-400">
                <span
                  aria-hidden="true"
                  className="size-1.5 shrink-0 rounded-full bg-indigo-600 dark:bg-indigo-400"
                />
                {fact}
              </span>
            </li>
          ))}
        </ul>
      </article>
    </RevealItem>
  );
}
