"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";
import { Eyebrow, Reveal } from "@/components/marketing/section-primitives";

export function ShowcaseOutro() {
  const t = useTranslations("features.outro");

  return (
    <section className="neu-raised relative w-full overflow-hidden rounded-3xl bg-[#e0e5ec] p-8 sm:p-12 dark:bg-[#1e222b] border-none">
      <Reveal className="mx-auto flex max-w-2xl flex-col items-center text-center">
        <Eyebrow>{t("eyebrow")}</Eyebrow>

        <h2 className="mt-4 text-balance font-heading text-3xl font-bold tracking-tight text-slate-900 sm:text-4xl dark:text-slate-50">
          {t("title")}
        </h2>
        <p className="mt-4 text-pretty text-base font-medium leading-relaxed text-slate-600 dark:text-slate-300">
          {t("lead")}
        </p>

        <div className="mt-8 flex w-full flex-col items-center gap-3 sm:w-auto sm:flex-row sm:gap-4">
          <Link
            href="/register"
            className="neu-button-primary inline-flex h-12 w-full items-center justify-center rounded-2xl bg-indigo-600 hover:bg-indigo-700 text-white font-bold px-7 text-sm shadow-neu-raised-sm focus-visible:outline-2 focus-visible:outline-indigo-600 focus-visible:outline-offset-2 transition-all sm:w-auto sm:min-w-[190px]"
          >
            {t("primaryCta")}
          </Link>
          <Link
            href="/contact"
            className="neu-button inline-flex h-12 w-full items-center justify-center rounded-2xl px-7 text-sm font-semibold text-slate-700 dark:text-slate-200 focus-visible:outline-2 focus-visible:outline-indigo-600 focus-visible:outline-offset-2 transition-all sm:w-auto sm:min-w-[190px]"
          >
            {t("secondaryCta")}
          </Link>
        </div>
      </Reveal>
    </section>
  );
}
