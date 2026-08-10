"use client";

import { ArrowLeft, ArrowRight } from "lucide-react";
import { useTranslations } from "next-intl";
import { TECHNICAL_SECTIONS } from "@/features/showcase/lib/technical-sections";

interface TechnicalPagerProps {
  active: string;
  onSelect: (id: string) => void;
}

/* The tab bar is at the top of the page; a reader who has just finished a panel is at the
   bottom of it. This is the same move without the scroll back up. */
export function TechnicalPager({ active, onSelect }: TechnicalPagerProps) {
  const t = useTranslations("features.technical.nav");
  const index = TECHNICAL_SECTIONS.findIndex((section) => section.id === active);

  const previous = index > 0 ? TECHNICAL_SECTIONS[index - 1] : null;
  const next = index < TECHNICAL_SECTIONS.length - 1 ? TECHNICAL_SECTIONS[index + 1] : null;

  return (
    <nav
      aria-label={t("pagerTitle")}
      className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between sm:gap-4"
    >
      {previous ? (
        <button
          type="button"
          onClick={() => onSelect(previous.id)}
          className="neu-button group flex w-full items-center gap-3 rounded-2xl px-5 py-4 text-left beat-16th transition-all focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 sm:w-auto sm:min-w-[15rem]"
        >
          <ArrowLeft
            aria-hidden="true"
            className="size-4 shrink-0 text-indigo-600 beat-16th transition-transform group-hover:-translate-x-0.5 dark:text-indigo-400"
          />
          <span className="flex flex-col">
            <span className="font-mono text-[0.65rem] font-bold uppercase tracking-[0.18em] text-slate-500 dark:text-slate-400">
              {t("previous")}
            </span>
            <span className="mt-0.5 text-sm font-semibold text-slate-800 dark:text-slate-100">
              {t(previous.id)}
            </span>
          </span>
        </button>
      ) : (
        /* Keeps "next" pinned right on the first panel instead of letting it slide left. */
        <span aria-hidden="true" className="hidden sm:block" />
      )}

      {next ? (
        <button
          type="button"
          onClick={() => onSelect(next.id)}
          className="neu-button group flex w-full items-center justify-end gap-3 rounded-2xl px-5 py-4 text-right beat-16th transition-all focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 sm:w-auto sm:min-w-[15rem]"
        >
          <span className="flex flex-col">
            <span className="font-mono text-[0.65rem] font-bold uppercase tracking-[0.18em] text-slate-500 dark:text-slate-400">
              {t("next")}
            </span>
            <span className="mt-0.5 text-sm font-semibold text-slate-800 dark:text-slate-100">
              {t(next.id)}
            </span>
          </span>
          <ArrowRight
            aria-hidden="true"
            className="size-4 shrink-0 text-indigo-600 beat-16th transition-transform group-hover:translate-x-0.5 dark:text-indigo-400"
          />
        </button>
      ) : null}
    </nav>
  );
}
