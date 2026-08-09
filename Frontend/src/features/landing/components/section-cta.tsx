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
    <section className="relative w-full overflow-hidden border-none bg-[var(--neu-surface)] px-5 pb-44 pt-24 sm:px-8 sm:pb-52 sm:pt-28 md:pt-32">
      {/* The close is a single raised pedestal rather than copy floating on the plate —
          it is the one element on the page that should look pressable from across the
          room. It runs to the same edges as every other section; only the text inside
          is capped, for line length. */}
      <Reveal className="relative z-10 mx-auto w-full max-w-6xl">
        <div className="neu-raised-lg flex flex-col items-center rounded-[2rem] px-7 py-16 text-center sm:px-14 sm:py-20">
          <Eyebrow variant="chip">{t("eyebrow")}</Eyebrow>

          <h2 className="mt-6 max-w-3xl text-balance font-heading text-3xl font-bold tracking-tight text-slate-900 dark:text-slate-100 sm:text-4xl md:text-5xl">
            {t("headline")}
          </h2>
          <p className="mt-5 max-w-xl text-pretty text-base leading-relaxed text-slate-600 dark:text-slate-300 sm:text-lg">
            {t("subheadline")}
          </p>

          <div className="mt-10 flex w-full flex-col items-center justify-center gap-4 sm:w-auto sm:flex-row">
            <Link href="/register" className="w-full sm:w-auto">
              <button
                type="button"
                className="neu-button-primary key-press inline-flex h-12 w-full items-center justify-center rounded-2xl px-8 text-base font-bold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 sm:w-auto sm:min-w-[200px]"
              >
                {t("getStarted")}
              </button>
            </Link>
            <Link href="/features" className="w-full sm:w-auto">
              <button
                type="button"
                className="neu-button key-press inline-flex h-12 w-full items-center justify-center rounded-2xl px-8 text-base font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 sm:w-auto sm:min-w-[200px]"
              >
                {t("exploreFeatures")}
              </button>
            </Link>
          </div>

          <p className="mt-9 font-mono text-xs font-semibold uppercase tracking-[0.2em] text-slate-500 dark:text-slate-400">
            {t("footnote")}
          </p>
        </div>
      </Reveal>

      <KeyboardFooter />
    </section>
  );
}

function KeyboardFooter() {
  return (
    <div
      aria-hidden="true"
      className="pointer-events-none absolute inset-x-0 bottom-0 h-32 select-none opacity-30 dark:opacity-20 sm:h-36"
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
