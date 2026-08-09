"use client";

import Link from "next/link";
import { ArrowRight, ArrowUpRight } from "lucide-react";
import { useTranslations } from "next-intl";
import { Eyebrow } from "@/components/marketing/section-primitives";
import { WALKTHROUGH_MODULES, WALKTHROUGH_TALLY } from "@/features/showcase/lib/walkthrough-modules";

export function WalkthroughHero() {
  const t = useTranslations("features.walkthrough");

  return (
    <section className="relative w-full overflow-hidden border-b border-border bg-background px-5 pb-16 pt-28 sm:px-8 sm:pb-20 sm:pt-32">
      {/* A keybed along the bottom edge, faded out before it reaches the copy. */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-x-0 bottom-0 h-48 opacity-70 dark:opacity-50"
        style={{
          backgroundImage:
            "repeating-linear-gradient(to right, var(--border) 0, var(--border) 1px, transparent 1px, transparent 3.25rem)",
          maskImage: "linear-gradient(to top, black, transparent 85%)",
        }}
      />

      <div className="relative mx-auto grid w-full max-w-6xl gap-x-16 gap-y-14 lg:grid-cols-12 lg:items-start">
        <div className="lg:col-span-7">
          <Eyebrow>{t("hero.eyebrow")}</Eyebrow>

          <h1 className="mt-6 text-balance font-heading text-4xl font-semibold leading-[1.06] tracking-tight text-foreground sm:text-5xl md:text-6xl">
            {t("hero.title")}
          </h1>
          <p className="mt-6 max-w-xl text-pretty text-base leading-relaxed text-muted-foreground sm:text-lg">
            {t("hero.lead")}
          </p>

          <p className="mt-8 font-mono text-[0.7rem] uppercase tracking-[0.18em] text-muted-foreground">
            {t("hero.tally", WALKTHROUGH_TALLY)}
          </p>

          <Link
            href="/features/technical"
            className="group mt-8 inline-flex items-center gap-2 rounded-full border border-border bg-background px-4 py-2 text-sm font-medium text-muted-foreground beat-16th transition-colors hover:border-foreground/30 hover:text-foreground"
          >
            {t("hero.techLink")}
            <ArrowUpRight
              aria-hidden="true"
              className="size-4 beat-16th transition-transform group-hover:translate-x-0.5 group-hover:-translate-y-0.5"
            />
          </Link>
        </div>

        {/* The index is the page's table of contents; a module not written yet stays visible but inert,
            so the shape of the walkthrough is clear before it is finished. */}
        <nav aria-label={t("hero.indexTitle")} className="lg:col-span-5">
          <p className="font-mono text-[0.7rem] uppercase tracking-[0.2em] text-muted-foreground">
            {t("hero.indexTitle")}
          </p>

          <ul className="mt-4 divide-y divide-border overflow-hidden rounded-2xl border border-border bg-card">
            {WALKTHROUGH_MODULES.map(({ key, anchor, status, icon: Icon, steps, minutes }, position) => {
              const label = (
                <>
                  <span
                    aria-hidden="true"
                    className="key-white flex h-12 w-8 shrink-0 items-end justify-center pb-1.5 font-mono text-[0.68rem] font-semibold"
                  >
                    {String(position + 1).padStart(2, "0")}
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="flex items-center gap-2 text-base font-semibold tracking-tight sm:text-lg">
                      <Icon aria-hidden="true" className="size-4 shrink-0 text-muted-foreground" />
                      {t(`modules.${key}.title`)}
                    </span>
                    <span className="mt-1.5 block font-mono text-[0.66rem] uppercase tracking-[0.16em] text-muted-foreground">
                      {t("hero.moduleMeta", { steps, minutes })}
                    </span>
                  </span>
                </>
              );

              return (
                <li key={key}>
                  {status === "ready" ? (
                    <a
                      href={`#${anchor}`}
                      className="group flex items-center gap-4 px-4 py-4 text-foreground beat-16th transition-colors hover:bg-muted/50"
                    >
                      {label}
                      <ArrowRight
                        aria-hidden="true"
                        className="size-4 shrink-0 text-muted-foreground beat-16th transition-transform group-hover:translate-x-1 group-hover:text-foreground"
                      />
                    </a>
                  ) : (
                    <div className="flex items-center gap-4 px-4 py-4 text-muted-foreground/60">
                      {label}
                      <span className="shrink-0 rounded-full border border-border px-2.5 py-0.5 font-mono text-[0.62rem] uppercase tracking-[0.16em]">
                        {t("hero.statusSoon")}
                      </span>
                    </div>
                  )}
                </li>
              );
            })}
          </ul>
        </nav>
      </div>
    </section>
  );
}
