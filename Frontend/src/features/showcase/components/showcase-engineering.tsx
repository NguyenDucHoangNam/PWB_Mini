"use client";

import type { ReactNode } from "react";
import { useTranslations } from "next-intl";
import { RevealGroup, RevealItem, Section } from "@/components/marketing/section-primitives";

/** Stack tokens are product names — they stay out of the message bundles on purpose. */
const CASES = [
  {
    key: "library",
    index: "01",
    stack: ["Next.js", "TanStack Query", "Spring Boot", "PostgreSQL", "S3", "Elasticsearch"],
  },
  {
    key: "voiceTag",
    index: "02",
    stack: ["FFmpeg", "EBU R128", "Jaffree", "Spring Boot", "S3"],
  },
  {
    key: "liveRoom",
    index: "03",
    stack: ["WebSocket", "STOMP", "WebRTC", "Redis", "PostgreSQL"],
  },
] as const;

const DETAIL_KEYS = ["detail1", "detail2", "detail3", "detail4"] as const;

export function ShowcaseEngineering() {
  const t = useTranslations("features.engineering");

  return (
    <Section className="pt-16 sm:pt-20 md:pt-24">
      <RevealGroup className="space-y-16 lg:space-y-24">
        {CASES.map(({ key, index, stack }) => (
          <RevealItem key={key}>
            <article
              id={key}
              className="grid scroll-mt-24 gap-8 border-t border-border pt-10 lg:grid-cols-[12rem_1fr] lg:gap-14"
            >
              <div className="lg:sticky lg:top-24 lg:self-start">
                <span className="block font-mono text-5xl font-semibold leading-none text-muted-foreground/25">
                  {index}
                </span>
                <ul className="mt-6 flex flex-wrap gap-2 lg:flex-col lg:items-start">
                  {stack.map((tech) => (
                    <li
                      key={tech}
                      className="rounded-full border border-border bg-muted/40 px-3 py-1 font-mono text-[0.7rem] text-muted-foreground"
                    >
                      {tech}
                    </li>
                  ))}
                </ul>
              </div>

              <div>
                <h2 className="max-w-2xl text-balance font-heading text-2xl font-semibold tracking-tight text-foreground sm:text-3xl">
                  {t(`${key}.title`)}
                </h2>
                <p className="mt-3 max-w-2xl text-sm leading-relaxed text-muted-foreground sm:text-base">
                  {t(`${key}.subtitle`)}
                </p>

                <div className="mt-9 space-y-8">
                  <Block label={t("problemLabel")}>
                    <p className="max-w-2xl text-sm leading-relaxed text-muted-foreground sm:text-base">
                      {t(`${key}.problem`)}
                    </p>
                  </Block>

                  <Block label={t("approachLabel")}>
                    <p className="max-w-2xl text-sm leading-relaxed text-muted-foreground sm:text-base">
                      {t(`${key}.approach`)}
                    </p>
                  </Block>

                  <Block label={t("detailLabel")}>
                    <ul className="max-w-2xl divide-y divide-border border-y border-border">
                      {DETAIL_KEYS.map((detailKey) => (
                        <li
                          key={detailKey}
                          className="flex gap-3.5 py-3.5 text-sm leading-relaxed text-muted-foreground"
                        >
                          <span
                            aria-hidden="true"
                            className="mt-2 size-1 shrink-0 rounded-full bg-muted-foreground/60"
                          />
                          {t(`${key}.${detailKey}`)}
                        </li>
                      ))}
                    </ul>
                  </Block>
                </div>

                {/* The trade-off is the part worth reading — it gets its own frame. */}
                <div className="mt-9 max-w-2xl rounded-xl border border-border bg-muted/40 p-5 sm:p-6">
                  <span className="font-mono text-[0.7rem] uppercase tracking-[0.2em] text-muted-foreground">
                    {t("decisionLabel")}
                  </span>
                  <p className="mt-3 text-sm leading-relaxed text-foreground/85 sm:text-base">
                    {t(`${key}.decision`)}
                  </p>
                </div>
              </div>
            </article>
          </RevealItem>
        ))}
      </RevealGroup>
    </Section>
  );
}

function Block({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <span className="inline-flex items-center gap-3 font-mono text-[0.7rem] uppercase tracking-[0.2em] text-muted-foreground/80">
        <span aria-hidden="true" className="h-px w-5 bg-border" />
        {label}
      </span>
      <div className="mt-3">{children}</div>
    </div>
  );
}
