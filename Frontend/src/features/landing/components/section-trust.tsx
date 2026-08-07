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
} from "./section-primitives";

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
      {/* A bar of staff lines bleeding out of the top edge — decorative only. */}
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

      <RevealGroup className="relative mt-14 grid gap-px overflow-hidden rounded-2xl border border-border bg-border sm:grid-cols-2 lg:mt-16">
        {ITEMS.map(({ key, icon }) => (
          <TrustItem
            key={key}
            icon={icon}
            title={t(`${key}Title`)}
            body={t(`${key}Body`)}
          />
        ))}
      </RevealGroup>

      <Reveal className="relative mt-10 flex flex-wrap items-center gap-x-3 gap-y-3">
        <span className="font-mono text-xs uppercase tracking-[0.2em] text-muted-foreground">
          {t("stackLabel")}
        </span>
        <span aria-hidden="true" className="h-px w-6 bg-border" />
        <ul className="flex flex-wrap gap-2">
          {STACK.map((tool) => (
            <li
              key={tool}
              className="rounded-full border border-border bg-card px-3 py-1 font-mono text-xs text-muted-foreground"
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
    <RevealItem className="bg-card p-7 sm:p-8">
      <Icon className="size-5 text-foreground" aria-hidden="true" />
      <h3 className="mt-5 text-lg font-semibold tracking-tight text-foreground">{title}</h3>
      <p className="mt-2.5 text-sm leading-relaxed text-muted-foreground">{body}</p>
    </RevealItem>
  );
}
