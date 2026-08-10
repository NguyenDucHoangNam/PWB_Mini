"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";
import { Eyebrow, Reveal } from "@/components/marketing/section-primitives";

export function TechnicalOutro() {
  const t = useTranslations("features.technical.outro");

  return (
    <section className="neu-raised rounded-3xl bg-[#e0e5ec] p-6 dark:bg-[#1e222b] sm:p-10">
      <Reveal className="mx-auto flex max-w-2xl flex-col items-center text-center">
        <Eyebrow variant="chip">{t("eyebrow")}</Eyebrow>

        <h2 className="mt-5 text-balance font-heading text-3xl font-bold tracking-tight text-slate-900 dark:text-slate-50 sm:text-4xl">
          {t("title")}
        </h2>
        <p className="mt-5 text-pretty text-base font-medium leading-relaxed text-slate-600 dark:text-slate-300">
          {t("lead")}
        </p>

        <div className="mt-10 flex w-full flex-col items-center gap-3 sm:w-auto sm:flex-row sm:gap-4">
          <Link
            href="/features"
            className="neu-button-primary inline-flex h-11 w-full items-center justify-center rounded-full px-6 text-sm font-semibold beat-16th transition-all focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 sm:w-auto sm:min-w-[190px]"
          >
            {t("primaryCta")}
          </Link>
          <Link
            href="/register"
            className="neu-button inline-flex h-11 w-full items-center justify-center rounded-full px-6 text-sm font-semibold text-slate-700 beat-16th transition-all focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 dark:text-slate-200 sm:w-auto sm:min-w-[190px]"
          >
            {t("secondaryCta")}
          </Link>
        </div>
      </Reveal>
    </section>
  );
}
