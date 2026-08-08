"use client";

import Link from "next/link";
import { ArrowLeft, ArrowDown } from "lucide-react";
import { useTranslations } from "next-intl";
import { Eyebrow } from "@/components/marketing/section-primitives";

const CASES = [
  { key: "library", index: "01" },
  { key: "voiceTag", index: "02" },
  { key: "liveRoom", index: "03" },
] as const;

const STAT_KEYS = ["stat1", "stat2", "stat3"] as const;

export function TechnicalHero() {
  const t = useTranslations("features.technical");
  const tCase = useTranslations("features.engineering");

  return (
    <section className="relative w-full overflow-hidden border-b border-border bg-background px-5 pb-20 pt-28 sm:px-8 sm:pb-24 sm:pt-32">
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
        <Link
          href="/features"
          className="group inline-flex items-center gap-2 font-mono text-xs uppercase tracking-[0.16em] text-muted-foreground beat-16th transition-colors hover:text-foreground"
        >
          <ArrowLeft
            aria-hidden="true"
            className="size-3.5 beat-16th transition-transform group-hover:-translate-x-0.5"
          />
          {t("back")}
        </Link>

        <div className="mt-8">
          <Eyebrow>{t("eyebrow")}</Eyebrow>
        </div>

        <h1 className="mt-6 max-w-4xl text-balance font-heading text-4xl font-semibold tracking-tight text-foreground sm:text-5xl md:text-6xl">
          {t("title")}
        </h1>

        <p className="mt-6 max-w-2xl text-pretty text-base leading-relaxed text-muted-foreground sm:text-lg">
          {t("lead")}
        </p>

        <dl className="mt-14 grid max-w-3xl grid-cols-1 border-t border-border sm:grid-cols-3 sm:border-l">
          {STAT_KEYS.map((key) => (
            <div
              key={key}
              className="border-b border-border px-0 py-5 sm:border-r sm:border-b-0 sm:px-6"
            >
              <dt className="font-mono text-[0.7rem] uppercase tracking-[0.18em] text-muted-foreground">
                {t(`${key}Label`)}
              </dt>
              <dd className="mt-2 font-heading text-xl font-semibold tracking-tight text-foreground sm:text-2xl">
                {t(`${key}Value`)}
              </dd>
            </div>
          ))}
        </dl>

        <nav aria-label={t("tocTitle")} className="mt-14 max-w-3xl">
          <span className="font-mono text-[0.7rem] uppercase tracking-[0.2em] text-muted-foreground">
            {t("tocTitle")}
          </span>
          <ul className="mt-4 border-t border-border">
            {CASES.map(({ key, index }) => (
              <li key={key} className="border-b border-border">
                <a
                  href={`#${key}`}
                  className="group flex items-start gap-5 py-4 beat-16th transition-colors"
                >
                  <span className="font-mono text-xs leading-6 text-muted-foreground/60">
                    {index}
                  </span>
                  <span className="flex-1 text-sm leading-6 text-muted-foreground beat-16th transition-colors group-hover:text-foreground sm:text-base">
                    {tCase(`${key}.title`)}
                  </span>
                  <ArrowDown
                    aria-hidden="true"
                    className="mt-1 size-4 shrink-0 text-muted-foreground/40 beat-16th transition-transform group-hover:translate-y-0.5 group-hover:text-foreground"
                  />
                </a>
              </li>
            ))}
          </ul>
        </nav>
      </div>
    </section>
  );
}
