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
} from "./section-primitives";

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
      <article className="group relative flex h-full flex-col overflow-hidden rounded-2xl border border-border bg-card p-7 beat-16th transition-colors hover:border-foreground/25">
        {/* Hairline that sweeps in along the top edge on hover. */}
        <span
          aria-hidden="true"
          className="absolute inset-x-0 top-0 h-px origin-left scale-x-0 bg-foreground/30 beat transition-transform group-hover:scale-x-100"
        />

        <div className="flex items-start justify-between">
          <span className="flex size-11 items-center justify-center rounded-xl border border-border bg-background">
            <Icon className="size-5 text-foreground" aria-hidden="true" />
          </span>
          <span className="font-mono text-3xl font-semibold leading-none text-muted-foreground/25">
            {index}
          </span>
        </div>

        <h3 className="mt-7 text-xl font-semibold tracking-tight text-foreground">{title}</h3>
        <p className="mt-2.5 text-sm leading-relaxed text-muted-foreground">{description}</p>

        <ul className="mt-7 border-t border-border">
          {facts.map((fact) => (
            <li
              key={fact}
              className="flex items-center gap-2.5 border-b border-border py-3 font-mono text-xs text-muted-foreground last:border-b-0"
            >
              <span aria-hidden="true" className="size-1 shrink-0 rounded-full bg-muted-foreground/60" />
              {fact}
            </li>
          ))}
        </ul>
      </article>
    </RevealItem>
  );
}
