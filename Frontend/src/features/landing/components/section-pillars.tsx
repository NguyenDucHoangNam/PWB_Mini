"use client";

import type { LucideIcon } from "lucide-react";
import { ListMusic, Mic, Radio } from "lucide-react";
import { useTranslations } from "next-intl";
import {
  Eyebrow,
  Reveal,
  RevealGroup,
  RevealItem,
  Section,
  SectionLead,
  SectionTitle,
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
    <Section tone="raised">
      <Reveal>
        <Eyebrow>{t("eyebrow")}</Eyebrow>
        <SectionTitle>{t("title")}</SectionTitle>
        <SectionLead>{t("lead")}</SectionLead>
      </Reveal>

      <RevealGroup className="mt-14 grid gap-6 lg:mt-16 lg:grid-cols-3">
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
      <article className="neu-raised group relative flex h-full flex-col overflow-hidden rounded-3xl border-none bg-[#e0e5ec] p-7 sm:p-9 dark:bg-[#1e222b] transition-all hover:scale-[1.02]">
        <span
          aria-hidden="true"
          className="absolute inset-x-0 top-0 h-0.5 origin-left scale-x-0 bg-indigo-600 dark:bg-indigo-400 transition-transform group-hover:scale-x-100"
        />

        <div className="flex items-start justify-between">
          <span className="neu-pressed-sm flex size-12 items-center justify-center rounded-2xl bg-[#e0e5ec] text-indigo-600 dark:bg-[#1e222b] dark:text-indigo-400">
            <Icon className="size-5" aria-hidden="true" />
          </span>
          <span className="font-mono text-3xl font-bold leading-none text-slate-400/40 dark:text-slate-600/40">
            {index}
          </span>
        </div>

        <h3 className="mt-7 text-xl font-bold tracking-tight text-slate-900 dark:text-slate-100">{title}</h3>
        <p className="mt-2.5 text-sm leading-relaxed text-slate-600 dark:text-slate-300">{description}</p>

        <ul className="mt-7 border-t border-slate-300/40 dark:border-slate-700/40">
          {facts.map((fact) => (
            <li
              key={fact}
              className="flex items-center gap-2.5 border-b border-slate-300/40 dark:border-slate-700/40 py-3 font-mono text-xs text-slate-600 dark:text-slate-400 last:border-b-0"
            >
              <span aria-hidden="true" className="size-1.5 shrink-0 rounded-full bg-indigo-600 dark:bg-indigo-400" />
              {fact}
            </li>
          ))}
        </ul>
      </article>
    </RevealItem>
  );
}
