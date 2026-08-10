"use client";

import Link from "next/link";
import { ArrowRight, ArrowUpRight } from "lucide-react";
import { useTranslations } from "next-intl";
import { Eyebrow } from "@/components/marketing/section-primitives";
import { WALKTHROUGH_MODULES } from "@/features/showcase/lib/walkthrough-modules";

export function WalkthroughHero() {
  const t = useTranslations("features.walkthrough");

  return (
    <section className="neu-raised relative w-full min-h-[calc(100dvh-7rem)] sm:min-h-[calc(100dvh-8rem)] md:min-h-[calc(100dvh-9rem)] flex flex-col justify-center overflow-hidden rounded-3xl bg-[#e0e5ec] p-6 sm:p-10 dark:bg-[#1e222b] border-none">
      <div className="relative mx-auto grid w-full max-w-6xl gap-x-16 gap-y-12 lg:grid-cols-12 lg:items-center">
        <div className="lg:col-span-7">
          <Eyebrow>{t("hero.eyebrow")}</Eyebrow>

          <h1 className="mt-5 text-balance font-heading text-4xl font-bold leading-[1.08] tracking-tight text-slate-900 sm:text-5xl md:text-6xl dark:text-slate-50">
            {t("hero.title")}
          </h1>
          <p className="mt-5 max-w-xl text-pretty text-base font-medium leading-relaxed text-slate-600 sm:text-lg dark:text-slate-300">
            {t("hero.lead")}
          </p>

          <Link
            href="/features/technical"
            className="neu-button group mt-8 inline-flex items-center gap-2 rounded-full px-5 py-2.5 text-sm font-semibold text-slate-700 focus-visible:outline-2 focus-visible:outline-indigo-600 focus-visible:outline-offset-2 dark:text-slate-200"
          >
            <span>{t("hero.techLink")}</span>
            <ArrowUpRight
              aria-hidden="true"
              className="size-4 transition-transform group-hover:translate-x-0.5 group-hover:-translate-y-0.5"
            />
          </Link>
        </div>

        <nav aria-label={t("hero.indexTitle")} className="lg:col-span-5">
          <p className="font-mono text-xs uppercase tracking-[0.2em] font-bold text-slate-500 dark:text-slate-400">
            {t("hero.indexTitle")}
          </p>

          <ul className="neu-pressed mt-4 flex flex-col gap-2.5 rounded-3xl bg-[#e0e5ec] p-3 dark:bg-[#1e222b] border-none">
            {WALKTHROUGH_MODULES.map(({ key, anchor, status, icon: Icon, steps }, position) => {
              const label = (
                <>
                  <span
                    aria-hidden="true"
                    className="neu-raised flex h-10 w-9 shrink-0 items-center justify-center rounded-xl font-mono text-xs font-bold text-indigo-600 dark:text-indigo-400 bg-[#e0e5ec] dark:bg-[#1e222b]"
                  >
                    {String(position + 1).padStart(2, "0")}
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="flex items-center gap-2 text-base font-bold tracking-tight text-slate-900 dark:text-slate-100 sm:text-lg">
                      <Icon aria-hidden="true" className="size-4 shrink-0 text-indigo-600 dark:text-indigo-400" />
                      {t(`modules.${key}.title`)}
                    </span>
                  </span>
                </>
              );

              return (
                <li key={key}>
                  {status === "ready" ? (
                    <a
                      href={`#${anchor}`}
                      className="neu-raised-sm group flex items-center gap-4 rounded-2xl p-3 text-slate-900 dark:text-slate-100 bg-[#e0e5ec] dark:bg-[#1e222b] focus-visible:outline-2 focus-visible:outline-indigo-600 focus-visible:outline-offset-2 transition-all"
                    >
                      {label}
                      <ArrowRight
                        aria-hidden="true"
                        className="size-4 shrink-0 text-slate-400 transition-transform group-hover:translate-x-1 group-hover:text-indigo-600 dark:group-hover:text-indigo-400"
                      />
                    </a>
                  ) : (
                    <div className="flex items-center gap-4 rounded-2xl p-3 text-slate-400 dark:text-slate-500">
                      {label}
                      <span className="neu-pressed-sm shrink-0 rounded-full px-2.5 py-0.5 font-mono text-[0.62rem] uppercase tracking-[0.16em] text-slate-500 dark:text-slate-400">
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
