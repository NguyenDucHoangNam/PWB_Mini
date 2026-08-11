"use client";

import { useTranslations } from "next-intl";
import { Reveal, RevealGroup, RevealItem } from "@/components/marketing/section-primitives";
import {
  PROBLEM_TRAITS,
  REDIS_BLOCKS,
  REDIS_IDEAS,
  USES,
  USE_FACTS,
} from "@/features/showcase/lib/technical-redis-content";
import { CARD, Chapter, ChapterIndex, Prose, RULE, ids } from "./technical-chapter";

/* ------------------------------------------------- 01 · what the main database is wrong for */

function ProblemChapter() {
  const t = useTranslations("features.technical.redis.problem");

  return (
    <Chapter id="rd-problem" step={1} eyebrow={t("eyebrow")} title={t("title")} lead={t("lead")}>
      <RevealGroup className="mt-7 grid gap-5 md:grid-cols-3">
        {ids("traits", PROBLEM_TRAITS).map((key) => (
          <RevealItem key={key} className="h-full">
            <div className={`flex h-full flex-col ${CARD}`}>
              <span className="font-mono text-[0.62rem] font-bold uppercase tracking-[0.16em] text-indigo-600 dark:text-indigo-400">
                {t(`${key}.tag`)}
              </span>
              <h4 className="mt-2.5 font-heading text-base font-bold tracking-tight text-slate-900 dark:text-slate-50">
                {t(`${key}.title`)}
              </h4>
              <p className="mt-2 text-pretty text-sm leading-relaxed text-slate-600 dark:text-slate-300">
                {t(`${key}.body`)}
              </p>
            </div>
          </RevealItem>
        ))}
      </RevealGroup>

      <Reveal className="mt-7">
        <Prose>{t("closing")}</Prose>
      </Reveal>
    </Chapter>
  );
}

/* --------------------------------------------------------------------- 02 · what Redis is */

function WhatChapter() {
  const t = useTranslations("features.technical.redis.what");

  return (
    <Chapter id="rd-what" step={2} eyebrow={t("eyebrow")} title={t("title")} lead={t("lead")}>
      <Reveal className="mt-7">
        <div className={`rounded-2xl border p-5 sm:p-6 ${RULE}`}>
          <p className="max-w-3xl text-pretty text-base font-semibold leading-relaxed text-slate-900 dark:text-slate-50">
            {t("board.title")}
          </p>
          <p className="mt-3 max-w-3xl text-pretty text-sm leading-relaxed text-slate-600 dark:text-slate-300">
            {t("board.body")}
          </p>
        </div>
      </Reveal>

      <RevealGroup className="mt-7 flex flex-col gap-4">
        {ids("ideas", REDIS_IDEAS).map((key, index) => (
          <RevealItem key={key}>
            <div className={`flex gap-4 ${CARD}`}>
              <span
                aria-hidden="true"
                className="mt-0.5 font-mono text-xs font-bold tabular-nums text-indigo-600 dark:text-indigo-400"
              >
                {String(index + 1).padStart(2, "0")}
              </span>
              <div>
                <h4 className="font-heading text-base font-bold tracking-tight text-slate-900 dark:text-slate-50">
                  {t(`${key}.title`)}
                </h4>
                <p className="mt-2 text-pretty text-sm leading-relaxed text-slate-600 dark:text-slate-300">
                  {t(`${key}.body`)}
                </p>
              </div>
            </div>
          </RevealItem>
        ))}
      </RevealGroup>

      <Reveal className="mt-7">
        <Prose>{t("closing")}</Prose>
      </Reveal>
    </Chapter>
  );
}

/* ------------------------------------------------------------------ 03 · the three jobs */

function UsageChapter() {
  const t = useTranslations("features.technical.redis.usage");

  return (
    <Chapter id="rd-usage" step={3} eyebrow={t("eyebrow")} title={t("title")} lead={t("lead")}>
      {/* Stacked rather than a three-column grid: each job carries the same three facts under
          it, and side by side those rows would have to be read across three columns at once. */}
      <RevealGroup className="mt-7 flex flex-col gap-5">
        {ids("uses", USES).map((key, index) => (
          <RevealItem key={key}>
            <div className={CARD}>
              <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
                <span
                  aria-hidden="true"
                  className="font-mono text-xs font-bold tabular-nums text-indigo-600 dark:text-indigo-400"
                >
                  {String(index + 1).padStart(2, "0")}
                </span>
                <h4 className="font-heading text-base font-bold tracking-tight text-slate-900 dark:text-slate-50">
                  {t(`${key}.title`)}
                </h4>
              </div>

              <p className="mt-2.5 max-w-3xl text-pretty text-sm leading-relaxed text-slate-600 dark:text-slate-300">
                {t(`${key}.body`)}
              </p>

              {/* The same three questions under every job, so the three answers can be compared
                  down the column rather than hunted for inside three paragraphs. */}
              <dl
                className={`mt-4 grid gap-x-5 gap-y-4 border-t pt-4 sm:grid-cols-3 ${RULE}`}
              >
                {USE_FACTS.map((fact) => (
                  <div key={fact}>
                    <dt className="font-mono text-[0.62rem] font-bold uppercase tracking-[0.16em] text-slate-500 dark:text-slate-400">
                      {t(`facts.${fact}`)}
                    </dt>
                    <dd className="mt-1.5 text-pretty text-sm leading-relaxed text-slate-600 dark:text-slate-300">
                      {t(`${key}.${fact}`)}
                    </dd>
                  </div>
                ))}
              </dl>
            </div>
          </RevealItem>
        ))}
      </RevealGroup>
    </Chapter>
  );
}

/* ------------------------------------------------------------------------------------ shell */

export function TechnicalRedis() {
  const t = useTranslations("features.technical.redis");

  return (
    <article id="topic-redis" className={`rounded-3xl border p-6 sm:p-8 ${RULE}`}>
      <Reveal>
        <h3 className="font-heading text-xl font-bold tracking-tight text-slate-900 dark:text-slate-50 sm:text-2xl">
          {t("title")}
        </h3>
        <p className="mt-4 max-w-3xl text-pretty text-base leading-relaxed text-slate-600 dark:text-slate-300 sm:text-lg">
          {t("intro")}
        </p>
      </Reveal>

      <Reveal className="mt-7">
        <ChapterIndex
          label={t("toc.label")}
          blocks={REDIS_BLOCKS}
          anchorPrefix="rd"
          title={(block) => t(`toc.items.${block}`)}
        />
      </Reveal>

      <ProblemChapter />
      <WhatChapter />
      <UsageChapter />
    </article>
  );
}
