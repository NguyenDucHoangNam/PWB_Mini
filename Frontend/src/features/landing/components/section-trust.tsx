"use client";

import type { LucideIcon } from "lucide-react";
import { Fingerprint, KeyRound, Timer, Trash2 } from "lucide-react";
import { useTranslations } from "next-intl";
import {
  Reveal,
  RevealGroup,
  RevealItem,
  Section,
  SectionHeader,
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
  "S3",
  "FFmpeg",
  "WebSocket",
  "WebRTC",
];

export function SectionTrust() {
  const t = useTranslations("landing.trust");

  return (
    <Section tone="trough">
      <span
        aria-hidden="true"
        className="staff-lines pointer-events-none absolute inset-x-0 top-0 h-20"
        style={{ maskImage: FADE_DOWN, WebkitMaskImage: FADE_DOWN }}
      />

      <SectionHeader
        eyebrow={t("eyebrow")}
        title={t("title")}
        lead={t("lead")}
        align="center"
      />

      <RevealGroup className="relative mt-14 grid gap-7 sm:grid-cols-2 lg:mt-16">
        {ITEMS.map(({ key, icon }) => (
          <TrustItem
            key={key}
            icon={icon}
            title={t(`${key}Title`)}
            body={t(`${key}Body`)}
          />
        ))}
      </RevealGroup>

      {/* The stack sits in a tray rather than loose on the plate, so it reads as a
          footnote to the section instead of a fifth card. */}
      <Reveal className="relative mt-12">
        <div className="neu-pressed flex flex-col gap-5 rounded-3xl px-6 py-7 sm:flex-row sm:items-center sm:gap-7 sm:px-8">
          <span className="shrink-0 font-mono text-xs font-bold uppercase tracking-[0.2em] text-indigo-600 dark:text-indigo-400">
            {t("stackLabel")}
          </span>
          <ul className="flex flex-wrap gap-2.5">
            {STACK.map((tool) => (
              <li
                key={tool}
                className="neu-raised-sm rounded-full px-4 py-1.5 font-mono text-xs font-semibold text-slate-700 dark:text-slate-300"
              >
                {tool}
              </li>
            ))}
          </ul>
        </div>
      </Reveal>
    </Section>
  );
}

function TrustItem({ icon: Icon, title, body }: { icon: LucideIcon; title: string; body: string }) {
  return (
    <RevealItem className="h-full">
      <article className="neu-lift flex h-full items-start gap-5 rounded-3xl border-none p-7 sm:p-8">
        <span className="neu-pressed flex size-12 shrink-0 items-center justify-center rounded-2xl text-indigo-600 dark:text-indigo-400">
          <Icon className="size-5" aria-hidden="true" />
        </span>
        <div>
          <h3 className="text-lg font-bold tracking-tight text-slate-900 dark:text-slate-100">{title}</h3>
          <p className="mt-2.5 text-sm leading-relaxed text-slate-600 dark:text-slate-300">{body}</p>
        </div>
      </article>
    </RevealItem>
  );
}
