"use client";

import Link from "next/link";
import { ArrowDown, ArrowUpRight } from "lucide-react";
import { useTranslations } from "next-intl";
import { Eyebrow } from "@/components/marketing/section-primitives";

const STAT_KEYS = ["stat1", "stat2", "stat3"] as const;

export function ShowcaseHero() {
  const t = useTranslations("features.hero");

  return (
    <section className="relative w-full overflow-hidden border-b border-border bg-background px-5 pb-20 pt-28 sm:px-8 sm:pb-24 sm:pt-32">
      {/* Faint blueprint grid — a hint that this page is about how the thing is built. */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 opacity-[0.35] dark:opacity-25"
        style={{
          backgroundImage:
            "linear-gradient(to right, var(--border) 1px, transparent 1px), linear-gradient(to bottom, var(--border) 1px, transparent 1px)",
          backgroundSize: "72px 72px",
          maskImage: "radial-gradient(ellipse 80% 60% at 50% 0%, black, transparent 75%)",
        }}
      />

      <div className="relative mx-auto w-full max-w-6xl">
        <Eyebrow>{t("eyebrow")}</Eyebrow>

        <h1 className="mt-6 max-w-4xl text-balance font-heading text-4xl font-semibold tracking-tight text-foreground sm:text-5xl md:text-6xl">
          {t("title")}
        </h1>

        <p className="mt-6 max-w-2xl text-pretty text-base leading-relaxed text-muted-foreground sm:text-lg">
          {t("lead")}
        </p>

        <div className="mt-10 flex flex-wrap items-center gap-3">
          <a
            href="#guide"
            className="group inline-flex items-center gap-2.5 rounded-full bg-foreground px-6 py-3 text-sm font-medium text-background beat-16th transition-opacity hover:opacity-85"
          >
            {t("guideCta")}
            <ArrowDown
              aria-hidden="true"
              className="size-4 beat-16th transition-transform group-hover:translate-y-0.5"
            />
          </a>
          <Link
            href="/features/technical"
            className="group inline-flex items-center gap-2.5 rounded-full border border-border bg-background px-6 py-3 text-sm font-medium text-foreground beat-16th transition-colors hover:border-foreground/40"
          >
            {t("techCta")}
            <ArrowUpRight
              aria-hidden="true"
              className="size-4 beat-16th transition-transform group-hover:translate-x-0.5 group-hover:-translate-y-0.5"
            />
          </Link>
        </div>

        <dl className="mt-16 grid max-w-3xl grid-cols-1 border-t border-border sm:grid-cols-3 sm:border-l">
          {STAT_KEYS.map((key) => (
            <div
              key={key}
              className="border-b border-border px-0 py-5 sm:border-r sm:border-b-0 sm:px-6"
            >
              <dt className="font-mono text-[0.7rem] uppercase tracking-[0.18em] text-muted-foreground">
                {t(`${key}Label`)}
              </dt>
              <dd className="mt-2 font-heading text-2xl font-semibold tracking-tight text-foreground">
                {t(`${key}Value`)}
              </dd>
            </div>
          ))}
        </dl>
      </div>
    </section>
  );
}
