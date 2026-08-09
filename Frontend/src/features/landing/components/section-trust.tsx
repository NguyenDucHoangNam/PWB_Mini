"use client";

import type { LucideIcon } from "lucide-react";
import { Fingerprint, KeyRound, Timer, Trash2 } from "lucide-react";
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

const ITEMS = [
  { key: "item1", icon: Fingerprint },
  { key: "item2", icon: Timer },
  { key: "item3", icon: KeyRound },
  { key: "item4", icon: Trash2 },
] as const;

const FADE_DOWN = "linear-gradient(to bottom, black, transparent)";

const STACK = [
  "Next.js",
  "TypeScript",
  "Tailwind",
  "Spring Boot",
  "PostgreSQL",
  "Redis",
  "Kafka",
  "Elasticsearch",
  "S3",
  "FFmpeg",
  "WebSocket",
  "WebRTC",
];

export function SectionTrust() {
  const t = useTranslations("landing.trust");

  return (
    <Section tone="raised">
      <span
        aria-hidden="true"
        className="staff-lines absolute inset-x-0 top-0 h-20"
        style={{ maskImage: FADE_DOWN, WebkitMaskImage: FADE_DOWN }}
      />

      <Reveal className="relative">
        <Eyebrow>{t("eyebrow")}</Eyebrow>
        <SectionTitle>{t("title")}</SectionTitle>
        <SectionLead>{t("lead")}</SectionLead>
      </Reveal>

      <RevealGroup className="relative mt-14 grid gap-6 sm:grid-cols-2 lg:mt-16">
        {ITEMS.map(({ key, icon }) => (
          <TrustItem
            key={key}
            icon={icon}
            title={t(`${key}Title`)}
            body={t(`${key}Body`)}
          />
        ))}
      </RevealGroup>

      <Reveal className="relative mt-12 flex flex-wrap items-center gap-x-3 gap-y-3">
        <span className="font-mono text-xs font-bold uppercase tracking-[0.2em] text-indigo-600 dark:text-indigo-400">
          {t("stackLabel")}
        </span>
        <span aria-hidden="true" className="h-0.5 w-6 bg-indigo-600 dark:bg-indigo-400" />
        <ul className="flex flex-wrap gap-2.5">
          {STACK.map((tool) => (
            <li
              key={tool}
              className="neu-pressed-sm rounded-full bg-[#e0e5ec] px-4 py-1.5 font-mono text-xs font-semibold text-slate-700 dark:bg-[#1e222b] dark:text-slate-300"
            >
              {tool}
            </li>
          ))}
        </ul>
      </Reveal>
    </Section>
  );
}

function TrustItem({ icon: Icon, title, body }: { icon: LucideIcon; title: string; body: string }) {
  return (
    <RevealItem className="h-full">
      <article className="neu-raised flex h-full flex-col rounded-3xl border-none bg-[#e0e5ec] p-7 sm:p-8 dark:bg-[#1e222b]">
        <span className="neu-pressed-sm flex size-11 items-center justify-center rounded-2xl bg-[#e0e5ec] text-indigo-600 dark:bg-[#1e222b] dark:text-indigo-400">
          <Icon className="size-5" aria-hidden="true" />
        </span>
        <h3 className="mt-5 text-lg font-bold tracking-tight text-slate-900 dark:text-slate-100">{title}</h3>
        <p className="mt-2.5 text-sm leading-relaxed text-slate-600 dark:text-slate-300">{body}</p>
      </article>
    </RevealItem>
  );
}
