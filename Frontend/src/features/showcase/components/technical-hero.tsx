"use client";

import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { useTranslations } from "next-intl";
import { Eyebrow } from "@/components/marketing/section-primitives";

export function TechnicalHero() {
  const t = useTranslations("features.technical");

  return (
    <section className="neu-raised relative flex w-full flex-col justify-center overflow-hidden rounded-3xl bg-[#e0e5ec] p-6 dark:bg-[#1e222b] sm:p-10">
      <div className="relative mx-auto w-full max-w-6xl">
        <Link
          href="/features"
          className="neu-button group inline-flex items-center gap-2 rounded-full px-4 py-2 text-xs font-semibold uppercase tracking-[0.16em] text-slate-600 beat-16th transition-all focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 dark:text-slate-300"
        >
          <ArrowLeft
            aria-hidden="true"
            className="size-3.5 beat-16th transition-transform group-hover:-translate-x-0.5"
          />
          {t("back")}
        </Link>

        <div className="mt-8">
          <Eyebrow>{t("hero.eyebrow")}</Eyebrow>
        </div>

        <h1 className="mt-5 max-w-4xl text-balance font-heading text-4xl font-bold leading-[1.08] tracking-tight text-slate-900 dark:text-slate-50 sm:text-5xl md:text-6xl">
          {t("hero.title")}
        </h1>

        <p className="mt-5 max-w-2xl text-pretty text-base font-medium leading-relaxed text-slate-600 dark:text-slate-300 sm:text-lg">
          {t("hero.lead")}
        </p>
      </div>
    </section>
  );
}
