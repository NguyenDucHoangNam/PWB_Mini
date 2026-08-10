"use client";

import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { useTranslations } from "next-intl";
import { Reveal } from "@/components/marketing/section-primitives";

const CASE_KEYS = ["library", "voiceTag", "liveRoom"] as const;

export function ShowcaseBridge() {
  const t = useTranslations("features.bridge");

  return (
    <section className="neu-raised relative w-full overflow-hidden rounded-3xl bg-[#e0e5ec] p-6 sm:p-10 md:p-12 dark:bg-[#1e222b] border-none">
      <Reveal className="mx-auto w-full max-w-6xl">
        <span className="inline-flex items-center gap-3 font-mono text-xs font-bold uppercase tracking-[0.22em] text-indigo-600 dark:text-indigo-400">
          <span aria-hidden="true" className="h-0.5 w-7 rounded-full bg-indigo-600 dark:bg-indigo-400" />
          {t("eyebrow")}
        </span>

        <div className="mt-6 flex flex-col gap-6 sm:flex-row sm:items-center sm:justify-between">
          <h2 className="max-w-2xl text-balance font-heading text-3xl font-bold tracking-tight text-slate-900 sm:text-4xl md:text-[2.75rem] md:leading-[1.1] dark:text-slate-50">
            {t("title")}
          </h2>

          <Link
            href="/features/technical"
            className="neu-button-primary group inline-flex shrink-0 items-center gap-3 rounded-2xl bg-indigo-600 hover:bg-indigo-700 text-white font-bold px-7 py-3.5 shadow-neu-raised-sm focus-visible:outline-2 focus-visible:outline-indigo-600 focus-visible:outline-offset-2 transition-all"
          >
            <span>{t("cta")}</span>
            <ArrowRight
              aria-hidden="true"
              className="size-4 transition-transform group-hover:translate-x-1"
            />
          </Link>
        </div>
      </Reveal>
    </section>
  );
}
