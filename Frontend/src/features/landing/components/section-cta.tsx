"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";
import { Eyebrow, Reveal } from "@/components/marketing/section-primitives";

const WHITE_KEY_COUNT = 21;
const BLACK_KEY_SLOTS = [0, 1, 3, 4, 5];

const FADE_UP_MASK = "linear-gradient(to top, black 35%, transparent)";

export function SectionCta() {
  const t = useTranslations("landing.cta");

  return (
    <section className="relative w-full overflow-hidden bg-[#e0e5ec] dark:bg-[#1e222b] px-5 pt-24 pb-44 sm:px-8 sm:pt-28 sm:pb-52 md:pt-32 border-none">
      <Reveal className="relative z-10 mx-auto flex max-w-2xl flex-col items-center text-center">
        <Eyebrow>{t("eyebrow")}</Eyebrow>

        <h2 className="mt-5 text-balance font-heading text-3xl font-bold tracking-tight text-slate-900 dark:text-slate-100 sm:text-4xl md:text-5xl">
          {t("headline")}
        </h2>
        <p className="mt-5 text-pretty text-base leading-relaxed text-slate-600 dark:text-slate-300 sm:text-lg">
          {t("subheadline")}
        </p>

        <div className="mt-10 flex w-full flex-col items-center justify-center gap-4 sm:w-auto sm:flex-row">
          <Link href="/register" className="w-full sm:w-auto">
            <button
              type="button"
              className="neu-button-primary inline-flex h-12 w-full items-center justify-center rounded-2xl bg-indigo-600 hover:bg-indigo-700 text-white font-bold px-8 text-base shadow-neu-raised-sm focus-visible:outline-2 focus-visible:outline-indigo-600 focus-visible:outline-offset-2 transition-all sm:w-auto sm:min-w-[200px]"
            >
              {t("getStarted")}
            </button>
          </Link>
          <Link href="/features" className="w-full sm:w-auto">
            <button
              type="button"
              className="neu-button inline-flex h-12 w-full items-center justify-center rounded-2xl bg-[#e0e5ec] dark:bg-[#1e222b] px-8 text-base font-semibold text-slate-800 dark:text-slate-200 focus-visible:outline-2 focus-visible:outline-indigo-600 focus-visible:outline-offset-2 sm:w-auto sm:min-w-[200px]"
            >
              {t("exploreFeatures")}
            </button>
          </Link>
        </div>

        <p className="mt-8 font-mono text-xs font-semibold uppercase tracking-[0.2em] text-slate-500 dark:text-slate-400">
          {t("footnote")}
        </p>
      </Reveal>

      <KeyboardFooter />
    </section>
  );
}

function KeyboardFooter() {
  return (
    <div
      aria-hidden="true"
      className="pointer-events-none absolute inset-x-0 bottom-0 h-32 select-none sm:h-36 opacity-30 dark:opacity-20"
      style={{ maskImage: FADE_UP_MASK, WebkitMaskImage: FADE_UP_MASK }}
    >
      <div className="flex h-full w-full items-stretch">
        {Array.from({ length: WHITE_KEY_COUNT }, (_, index) => (
          <div key={index} className="relative flex-1">
            <div className="key-white h-full w-full" />
            {BLACK_KEY_SLOTS.includes(index % 7) && (
              <div className="key-black absolute right-0 top-0 z-10 h-3/5 w-3/5 translate-x-1/2" />
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
