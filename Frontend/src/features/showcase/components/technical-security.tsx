"use client";

import { Fragment } from "react";
import { ArrowDown, ArrowRight } from "lucide-react";
import { useTranslations } from "next-intl";
import { Reveal, RevealGroup, RevealItem } from "@/components/marketing/section-primitives";
import {
  CHAIN_GROUPS,
  CHAIN_LAYERS,
  LIMITER_ROWS,
  ORDER_RISKS,
  RATE_LIMIT_LAYER,
  RISKS,
  RULE_TIERS,
  SECURITY_BLOCKS,
  SIGNALS,
} from "@/features/showcase/lib/technical-security-content";
import { CARD, Chapter, ChapterIndex, Prose, RULE, ids } from "./technical-chapter";

/* ------------------------------------------------------------------- 01 · the chain, drawn */

function LayerBox({ index }: { index: number }) {
  const t = useTranslations("features.technical.security.chain.layers");
  const isLimiter = index === RATE_LIMIT_LAYER;

  return (
    <div
      className={[
        "flex flex-1 flex-col rounded-xl border p-4",
        isLimiter
          ? "border-indigo-500/50 dark:border-indigo-400/50"
          : "border-slate-300/70 dark:border-slate-600/50",
      ].join(" ")}
    >
      <span
        aria-hidden="true"
        className={[
          "font-mono text-[0.68rem] font-bold tabular-nums",
          isLimiter
            ? "text-indigo-600 dark:text-indigo-400"
            : "text-slate-500 dark:text-slate-400",
        ].join(" ")}
      >
        {String(index + 1).padStart(2, "0")}
      </span>
      <span
        className={[
          "mt-1.5 font-heading text-sm font-bold tracking-tight",
          isLimiter
            ? "text-indigo-700 dark:text-indigo-300"
            : "text-slate-900 dark:text-slate-50",
        ].join(" ")}
      >
        {t(`${index}.title`)}
      </span>
      <span className="mt-1 font-mono text-[0.6rem] font-bold uppercase tracking-[0.12em] text-slate-500 dark:text-slate-400">
        {t(`${index}.role`)}
      </span>
    </div>
  );
}

/* Between two boxes: sideways on a wide screen, downwards once the row has wrapped into a
   column. Both are the same arrow, so only the direction is swapped rather than the meaning. */
function Step() {
  return (
    <div aria-hidden="true" className="flex items-center justify-center">
      <ArrowRight className="hidden size-4 text-slate-400 dark:text-slate-500 sm:block" />
      <ArrowDown className="size-4 text-slate-400 dark:text-slate-500 sm:hidden" />
    </div>
  );
}

/* Boxes and bands in flex, not on a fixed canvas: this is a line, not a two-dimensional graph,
   so it can reflow instead of scrolling sideways on a phone. */
function ChainDiagram() {
  const t = useTranslations("features.technical.security.chain");

  return (
    <figure className="mt-8">
      {CHAIN_GROUPS.map((group, groupIndex) => (
        <Fragment key={group.id}>
          {groupIndex > 0 ? (
            <div aria-hidden="true" className="flex justify-center py-3">
              <ArrowDown className="size-5 text-slate-400 dark:text-slate-500" />
            </div>
          ) : null}

          <div className={`rounded-2xl border p-4 sm:p-5 ${RULE}`}>
            <span className="font-mono text-[0.62rem] font-bold uppercase tracking-[0.16em] text-slate-500 dark:text-slate-400">
              {t(`groups.${group.id}.label`)}
            </span>
            {/* Boxes share the width evenly on a wide screen and stack on a narrow one. Both
                bands use the same row so a two-box band and a three-box band stay the same
                height, which is what makes them read as one continuous line. */}
            <div className="mt-3 flex flex-col gap-2 sm:flex-row">
              {Array.from({ length: group.count }, (_, offset) => group.from + offset).map(
                (index, position) => (
                  <Fragment key={index}>
                    {position > 0 ? <Step /> : null}
                    <LayerBox index={index} />
                  </Fragment>
                ),
              )}
            </div>
          </div>
        </Fragment>
      ))}

      <div aria-hidden="true" className="flex justify-center py-3">
        <ArrowDown className="size-5 text-slate-400 dark:text-slate-500" />
      </div>

      <div className="rounded-2xl border border-dashed border-slate-400/60 p-4 text-center dark:border-slate-500/60">
        <span className="font-heading text-sm font-bold tracking-tight text-slate-900 dark:text-slate-50">
          {t("destination.title")}
        </span>
        <span className="mt-1 block text-sm text-slate-600 dark:text-slate-300">
          {t("destination.body")}
        </span>
      </div>

      {/* The drawing is decoration for a screen reader — the same sequence is spelled out in the
          descriptions below, so the figure only needs to say what it is. */}
      <figcaption className="sr-only">{t("caption")}</figcaption>
    </figure>
  );
}

function ChainChapter() {
  const t = useTranslations("features.technical.security.chain");

  return (
    <Chapter id="sc-chain" step={1} eyebrow={t("eyebrow")} title={t("title")} lead={t("lead")}>
      <ChainDiagram />

      <Reveal className="mt-10">
        <h4 className="font-heading text-lg font-bold tracking-tight text-slate-900 dark:text-slate-50">
          {t("detail.title")}
        </h4>
        <p className="mt-3 max-w-3xl text-pretty text-base leading-relaxed text-slate-600 dark:text-slate-300">
          {t("detail.lead")}
        </p>
      </Reveal>

      {/* Divided rows rather than five more cards: the drawing above already gave each layer a
          box, and boxing them twice would read as two different sets of things. */}
      <RevealGroup
        className={`mt-6 flex flex-col divide-y border-y ${RULE} divide-slate-300/70 dark:divide-slate-600/50`}
      >
        {ids("layers", CHAIN_LAYERS).map((key, index) => (
          <RevealItem key={key}>
            <div className="flex gap-4 py-5 sm:gap-5">
              <span
                aria-hidden="true"
                className={[
                  "mt-0.5 font-mono text-xs font-bold tabular-nums",
                  index === RATE_LIMIT_LAYER
                    ? "text-indigo-600 dark:text-indigo-400"
                    : "text-slate-500 dark:text-slate-400",
                ].join(" ")}
              >
                {String(index + 1).padStart(2, "0")}
              </span>
              <div>
                <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
                  <h5 className="font-heading text-base font-bold tracking-tight text-slate-900 dark:text-slate-50">
                    {t(`layers.${index}.title`)}
                  </h5>
                  <span className="font-mono text-[0.62rem] font-bold uppercase tracking-[0.14em] text-slate-500 dark:text-slate-400">
                    {t(`layers.${index}.role`)}
                  </span>
                </div>
                <p className="mt-2 max-w-3xl text-pretty text-sm leading-relaxed text-slate-600 dark:text-slate-300">
                  {t(`layers.${index}.body`)}
                </p>
              </div>
            </div>
          </RevealItem>
        ))}
      </RevealGroup>

      <Reveal className="mt-9">
        <h4 className="font-heading text-lg font-bold tracking-tight text-slate-900 dark:text-slate-50">
          {t("order.title")}
        </h4>
        <p className="mt-3 max-w-3xl text-pretty text-base leading-relaxed text-slate-600 dark:text-slate-300">
          {t("order.lead")}
        </p>
      </Reveal>

      {/* Two wrong positions side by side, the same device the queue chapter uses for the two
          wrong ways to send an event. Neither throws, which is the whole point. */}
      <Reveal className="mt-6">
        <div className={`grid gap-px overflow-hidden rounded-2xl border ${RULE} sm:grid-cols-2`}>
          {ids("order.risks", ORDER_RISKS).map((key) => (
            <div key={key} className="p-5 sm:p-6">
              <span className="font-mono text-[0.62rem] font-bold uppercase tracking-[0.16em] text-rose-700 dark:text-rose-400">
                {t(`${key}.tag`)}
              </span>
              <p className="mt-3 text-pretty text-base font-semibold leading-relaxed text-slate-900 dark:text-slate-50">
                {t(`${key}.title`)}
              </p>
              <p className="mt-2.5 text-pretty text-sm leading-relaxed text-slate-600 dark:text-slate-300">
                {t(`${key}.body`)}
              </p>
            </div>
          ))}
        </div>
      </Reveal>

      <Reveal className="mt-7">
        <Prose>{t("order.closing")}</Prose>
      </Reveal>
    </Chapter>
  );
}

/* ------------------------------------------------------------ 02 · what rate limiting is */

function WhatChapter() {
  const t = useTranslations("features.technical.security.what");

  return (
    <Chapter id="sc-what" step={2} eyebrow={t("eyebrow")} title={t("title")} lead={t("lead")}>
      <Reveal className="mt-7">
        <div className="rounded-2xl border border-indigo-500/40 p-5 dark:border-indigo-400/40 sm:p-6">
          <span className="font-mono text-[0.62rem] font-bold uppercase tracking-[0.16em] text-indigo-600 dark:text-indigo-400">
            {t("idea.tag")}
          </span>
          <p className="mt-3 max-w-3xl text-pretty text-base font-semibold leading-relaxed text-slate-900 dark:text-slate-50 sm:text-lg">
            {t("idea.title")}
          </p>
          <p className="mt-3 max-w-3xl text-pretty text-sm leading-relaxed text-slate-600 dark:text-slate-300">
            {t("idea.body")}
          </p>
        </div>
      </Reveal>

      <RevealGroup className="mt-7 grid gap-5 md:grid-cols-3">
        {ids("risks", RISKS).map((key) => (
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

/* ---------------------------------------------------------------- 03 · how it is built here */

function HowChapter() {
  const t = useTranslations("features.technical.security.how");

  return (
    <Chapter id="sc-how" step={3} eyebrow={t("eyebrow")} title={t("title")} lead={t("lead")}>
      <Reveal className="mt-7">
        <div className={`rounded-2xl border p-5 sm:p-6 ${RULE}`}>
          <p className="max-w-3xl text-pretty text-base font-semibold leading-relaxed text-slate-900 dark:text-slate-50">
            {t("subject.title")}
          </p>
          <p className="mt-3 max-w-3xl text-pretty text-sm leading-relaxed text-slate-600 dark:text-slate-300">
            {t("subject.body")}
          </p>
        </div>
      </Reveal>

      <Reveal className="mt-9">
        <h4 className="font-heading text-lg font-bold tracking-tight text-slate-900 dark:text-slate-50">
          {t("tiers.title")}
        </h4>
        <p className="mt-3 max-w-3xl text-pretty text-base leading-relaxed text-slate-600 dark:text-slate-300">
          {t("tiers.lead")}
        </p>
      </Reveal>

      <RevealGroup className="mt-6 grid gap-5 md:grid-cols-3">
        {ids("tiers.items", RULE_TIERS).map((key) => (
          <RevealItem key={key} className="h-full">
            <div className={`flex h-full flex-col ${CARD}`}>
              <span className="font-mono text-[0.72rem] font-bold text-indigo-600 dark:text-indigo-400">
                {t(`${key}.figure`)}
              </span>
              <h5 className="mt-2.5 font-heading text-base font-bold tracking-tight text-slate-900 dark:text-slate-50">
                {t(`${key}.title`)}
              </h5>
              <p className="mt-2 text-pretty text-sm leading-relaxed text-slate-600 dark:text-slate-300">
                {t(`${key}.body`)}
              </p>
            </div>
          </RevealItem>
        ))}
      </RevealGroup>

      <Reveal className="mt-9">
        <h4 className="font-heading text-lg font-bold tracking-tight text-slate-900 dark:text-slate-50">
          {t("signals.title")}
        </h4>
      </Reveal>

      <RevealGroup className="mt-5 grid gap-5 md:grid-cols-2">
        {ids("signals.items", SIGNALS).map((key) => (
          <RevealItem key={key} className="h-full">
            <div className={`flex h-full flex-col ${CARD}`}>
              <h5 className="font-heading text-base font-bold tracking-tight text-slate-900 dark:text-slate-50">
                {t(`${key}.title`)}
              </h5>
              <p className="mt-2 text-pretty text-sm leading-relaxed text-slate-600 dark:text-slate-300">
                {t(`${key}.body`)}
              </p>
            </div>
          </RevealItem>
        ))}
      </RevealGroup>

      <Reveal className="mt-9">
        <h4 className="font-heading text-lg font-bold tracking-tight text-slate-900 dark:text-slate-50">
          {t("others.title")}
        </h4>
        <p className="mt-3 max-w-3xl text-pretty text-base leading-relaxed text-slate-600 dark:text-slate-300">
          {t("others.lead")}
        </p>
      </Reveal>

      {/* Three columns cannot shrink below the width their longest cell needs, so the table gets
          its own scroller rather than pushing the whole page sideways on a phone. */}
      <Reveal className="mt-6">
        <div className="neu-scroll-thin overflow-x-auto">
          <table className="w-full min-w-[38rem] border-collapse text-left">
            <thead>
              <tr>
                {["a", "b", "c"].map((col) => (
                  <th
                    key={col}
                    scope="col"
                    className={`border-b pb-2.5 pr-5 font-mono text-[0.62rem] font-bold uppercase tracking-[0.16em] text-slate-500 dark:text-slate-400 ${RULE}`}
                  >
                    {t(`others.head.${col}`)}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {ids("others.rows", LIMITER_ROWS).map((key, index) => (
                <tr key={key}>
                  <td
                    className={`border-b py-3 pr-5 align-top text-sm font-semibold ${RULE} ${
                      index === 0
                        ? "text-indigo-700 dark:text-indigo-300"
                        : "text-slate-800 dark:text-slate-100"
                    }`}
                  >
                    {t(`${key}.a`)}
                  </td>
                  <td
                    className={`border-b py-3 pr-5 align-top text-sm text-slate-600 dark:text-slate-300 ${RULE}`}
                  >
                    {t(`${key}.b`)}
                  </td>
                  <td
                    className={`border-b py-3 align-top text-sm text-slate-600 dark:text-slate-300 ${RULE}`}
                  >
                    {t(`${key}.c`)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Reveal>

      <Reveal className="mt-7">
        <Prose>{t("closing")}</Prose>
      </Reveal>
    </Chapter>
  );
}

/* ------------------------------------------------------------------------------------ shell */

export function TechnicalSecurity() {
  const t = useTranslations("features.technical.security");

  return (
    <article id="topic-security" className={`rounded-3xl border p-6 sm:p-8 ${RULE}`}>
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
          blocks={SECURITY_BLOCKS}
          anchorPrefix="sc"
          title={(block) => t(`toc.items.${block}`)}
        />
      </Reveal>

      <ChainChapter />
      <WhatChapter />
      <HowChapter />
    </article>
  );
}
