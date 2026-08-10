"use client";

import type { ReactNode } from "react";
import { Radio } from "lucide-react";
import { useTranslations } from "next-intl";
import { Reveal, RevealGroup, RevealItem } from "@/components/marketing/section-primitives";
import { PUBSUB_DIAGRAM, REALTIME_DIAGRAM } from "@/features/showcase/lib/technical-diagrams";
import {
  CHANNELS,
  FLOW_STEPS,
  PROBLEM_POINTS,
  PUBSUB_KINDS,
  REALTIME_BLOCKS,
  SAMPLE_FRAME,
  STOMP_GAPS,
  WEBSOCKET_POINTS,
} from "@/features/showcase/lib/technical-realtime-content";
import { TechnicalDiagram } from "./technical-diagram";
import { TechnicalSectionShell } from "./technical-shell";

/* Flat by exception. Every other panel is moulded, and this one is not, because it is the only
   panel whose job is to teach something the reader does not already know: shadows on cards
   inside cards make a long explanation read as a pile of controls instead of a page of prose.
   The trade is that this section no longer matches the others exactly — worth it here and
   nowhere else on the page. */

const CARD = "rounded-2xl border border-slate-300/70 p-5 dark:border-slate-600/50";
const RULE = "border-slate-300/70 dark:border-slate-600/50";

function ids(prefix: string, count: number) {
  return Array.from({ length: count }, (_, index) => `${prefix}.${index}`);
}

/* One numbered chapter. The number is the reader's place in the argument, so it is part of the
   heading rather than decoration beside it. */
function Chapter({
  id,
  step,
  eyebrow,
  title,
  lead,
  children,
}: {
  id: string;
  step: number;
  eyebrow: string;
  title: string;
  lead?: string;
  children: ReactNode;
}) {
  return (
    <section className="mt-16 first:mt-12">
      <Reveal>
        {/* scroll-mt keeps the heading clear of the sticky page header when the contents
            list jumps here. */}
        <div id={`rt-${id}`} className="scroll-mt-28">
          <div className="flex items-center gap-3">
            <span className="font-mono text-sm font-bold tabular-nums text-indigo-600 dark:text-indigo-400">
              {String(step).padStart(2, "0")}
            </span>
            <span className="font-mono text-[0.62rem] font-bold uppercase tracking-[0.2em] text-slate-500 dark:text-slate-400">
              {eyebrow}
            </span>
          </div>

          <h3 className="mt-3 max-w-3xl text-balance font-heading text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-50 sm:text-3xl">
            {title}
          </h3>

          {lead ? (
            <p className="mt-4 max-w-3xl text-pretty text-base leading-relaxed text-slate-600 dark:text-slate-300 sm:text-lg">
              {lead}
            </p>
          ) : null}
        </div>
      </Reveal>

      {children}
    </section>
  );
}

function Prose({ children }: { children: ReactNode }) {
  return (
    <p className="max-w-3xl text-pretty text-base leading-relaxed text-slate-600 dark:text-slate-300">
      {children}
    </p>
  );
}

/* ------------------------------------------------------------------- 01 · request / response */

function ProblemChapter() {
  const t = useTranslations("features.technical.realtime.problem");

  return (
    <Chapter
      id="problem"
      step={1}
      eyebrow={t("eyebrow")}
      title={t("title")}
      lead={t("lead")}
    >
      <RevealGroup className="mt-7 flex flex-col gap-5">
        {ids("points", PROBLEM_POINTS).map((key) => (
          <RevealItem key={key}>
            <Prose>{t(key)}</Prose>
          </RevealItem>
        ))}
      </RevealGroup>

      {/* The two columns are the chapter's conclusion in one glance: what the web gives you,
          and what a shared listening room actually needs. Everything after this exists to
          close that gap. */}
      <Reveal className="mt-8">
        <div className={`grid gap-px overflow-hidden rounded-2xl border ${RULE} sm:grid-cols-2`}>
          {["have", "need"].map((side) => (
            <div key={side} className="p-5 sm:p-6">
              <span
                className={[
                  "font-mono text-[0.62rem] font-bold uppercase tracking-[0.16em]",
                  side === "need"
                    ? "text-indigo-600 dark:text-indigo-400"
                    : "text-slate-500 dark:text-slate-400",
                ].join(" ")}
              >
                {t(`compare.${side}.tag`)}
              </span>
              <p className="mt-3 text-pretty text-base font-semibold leading-relaxed text-slate-900 dark:text-slate-50">
                {t(`compare.${side}.title`)}
              </p>
              <p className="mt-2.5 text-pretty text-sm leading-relaxed text-slate-600 dark:text-slate-300">
                {t(`compare.${side}.body`)}
              </p>
            </div>
          ))}
        </div>
      </Reveal>
    </Chapter>
  );
}

/* ------------------------------------------------------------------------- 02 · the socket */

function WebSocketChapter() {
  const t = useTranslations("features.technical.realtime.websocket");

  return (
    <Chapter
      id="websocket"
      step={2}
      eyebrow={t("eyebrow")}
      title={t("title")}
      lead={t("lead")}
    >
      <RevealGroup className="mt-7 grid gap-5 md:grid-cols-2">
        {ids("points", WEBSOCKET_POINTS).map((key) => (
          <RevealItem key={key} className="h-full">
            <div className={`flex h-full flex-col ${CARD}`}>
              <h4 className="font-heading text-base font-bold tracking-tight text-slate-900 dark:text-slate-50">
                {t(`${key}.title`)}
              </h4>
              <p className="mt-2.5 text-pretty text-sm leading-relaxed text-slate-600 dark:text-slate-300">
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

/* --------------------------------------------------------------------------- 03 · why STOMP */

function StompChapter() {
  const t = useTranslations("features.technical.realtime.stomp");

  return (
    <Chapter id="stomp" step={3} eyebrow={t("eyebrow")} title={t("title")}>
      {/* The reader's own objection, quoted back at them before it is answered. Stating it
          plainly is what stops the rest of the chapter reading as unprompted detail. */}
      <Reveal className="mt-6">
        <blockquote
          className={`max-w-3xl border-l-2 border-indigo-500/70 py-1 pl-5 text-pretty text-lg font-medium italic leading-relaxed text-slate-700 dark:border-indigo-400/70 dark:text-slate-200`}
        >
          {t("question")}
        </blockquote>
      </Reveal>

      <Reveal className="mt-6">
        <Prose>{t("answer")}</Prose>
      </Reveal>

      <RevealGroup className="mt-7 flex flex-col gap-4">
        {ids("gaps", STOMP_GAPS).map((key, index) => (
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

      {/* The comparison that does the actual explaining. It is set apart because a reader who
          takes only one thing from this chapter should take this one. */}
      <Reveal className="mt-8">
        <div className={`rounded-2xl border border-indigo-500/40 p-5 dark:border-indigo-400/40 sm:p-6`}>
          <span className="font-mono text-[0.62rem] font-bold uppercase tracking-[0.16em] text-indigo-600 dark:text-indigo-400">
            {t("analogy.tag")}
          </span>
          <p className="mt-3 max-w-3xl text-pretty text-base font-semibold leading-relaxed text-slate-900 dark:text-slate-50 sm:text-lg">
            {t("analogy.title")}
          </p>
          <p className="mt-3 max-w-3xl text-pretty text-sm leading-relaxed text-slate-600 dark:text-slate-300">
            {t("analogy.body")}
          </p>
        </div>
      </Reveal>

      {/* One message, printed. "A set of text conventions" stays abstract until you see that
          the convention is a word on the first line and a channel name on the second. */}
      <Reveal className="mt-8">
        <figure>
          <figcaption className="text-pretty text-sm leading-relaxed text-slate-600 dark:text-slate-300">
            {t("sample.caption")}
          </figcaption>
          <pre
            className={`mt-3 overflow-x-auto rounded-2xl border p-5 font-mono text-[0.78rem] leading-relaxed text-slate-700 dark:text-slate-200 ${RULE}`}
          >
            <code>{SAMPLE_FRAME}</code>
          </pre>
        </figure>
      </Reveal>
    </Chapter>
  );
}

/* ------------------------------------------------------------------------------ 04 · pub/sub */

function PubSubChapter() {
  const t = useTranslations("features.technical.realtime.pubsub");

  return (
    <Chapter id="pubsub" step={4} eyebrow={t("eyebrow")} title={t("title")} lead={t("lead")}>
      <Reveal className="mt-7">
        <div className={`rounded-2xl border p-5 sm:p-6 ${RULE}`}>
          <p className="max-w-3xl text-pretty text-base font-semibold leading-relaxed text-slate-900 dark:text-slate-50">
            {t("radio.title")}
          </p>
          <p className="mt-3 max-w-3xl text-pretty text-sm leading-relaxed text-slate-600 dark:text-slate-300">
            {t("radio.body")}
          </p>
        </div>
      </Reveal>

      <Reveal className="mt-7">
        <TechnicalDiagram spec={PUBSUB_DIAGRAM} flat />
      </Reveal>

      <RevealGroup className="mt-7 grid gap-5 md:grid-cols-2">
        {ids("kinds", PUBSUB_KINDS).map((key) => (
          <RevealItem key={key} className="h-full">
            <div className={`flex h-full flex-col ${CARD}`}>
              <h4 className="font-heading text-base font-bold tracking-tight text-slate-900 dark:text-slate-50">
                {t(`${key}.title`)}
              </h4>
              <p className="mt-2.5 text-pretty text-sm leading-relaxed text-slate-600 dark:text-slate-300">
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

/* ------------------------------------------------------------------------ 05 · what was built */

function SystemChapter() {
  const t = useTranslations("features.technical.realtime.system");

  return (
    <Chapter id="system" step={5} eyebrow={t("eyebrow")} title={t("title")} lead={t("lead")}>
      {/* The whole path first, then the same path one stage at a time. Seeing the shape before
          the detail is what lets a reader place each step as it arrives. */}
      <Reveal className="mt-7">
        <TechnicalDiagram spec={REALTIME_DIAGRAM} flat />
      </Reveal>

      <Reveal className="mt-10">
        <h4 className="font-heading text-lg font-bold tracking-tight text-slate-900 dark:text-slate-50">
          {t("flow.title")}
        </h4>
        <p className="mt-3 max-w-3xl text-pretty text-base leading-relaxed text-slate-600 dark:text-slate-300">
          {t("flow.lead")}
        </p>
      </Reveal>

      <RevealGroup className="mt-6 flex flex-col">
        {ids("flow.steps", FLOW_STEPS).map((key, index) => (
          <RevealItem key={key}>
            <div className="flex gap-4 sm:gap-5">
              {/* The rail belongs to the spacer column, not to a border on the card, so it
                  keeps running through the gap between two stages. */}
              <div className="flex flex-col items-center">
                <span
                  className={`flex size-9 shrink-0 items-center justify-center rounded-full border font-mono text-xs font-bold tabular-nums text-indigo-600 dark:text-indigo-400 ${RULE}`}
                >
                  {index + 1}
                </span>
                {index < FLOW_STEPS - 1 ? (
                  <span
                    aria-hidden="true"
                    className="my-1 w-px grow bg-slate-300/70 dark:bg-slate-600/50"
                  />
                ) : null}
              </div>

              <div className={index < FLOW_STEPS - 1 ? "pb-7" : undefined}>
                <h5 className="font-heading text-base font-bold tracking-tight text-slate-900 dark:text-slate-50">
                  {t(`${key}.title`)}
                </h5>
                <p className="mt-2 max-w-3xl text-pretty text-sm leading-relaxed text-slate-600 dark:text-slate-300">
                  {t(`${key}.body`)}
                </p>
              </div>
            </div>
          </RevealItem>
        ))}
      </RevealGroup>

      <Reveal className="mt-10">
        <h4 className="font-heading text-lg font-bold tracking-tight text-slate-900 dark:text-slate-50">
          {t("channels.title")}
        </h4>
        <p className="mt-3 max-w-3xl text-pretty text-base leading-relaxed text-slate-600 dark:text-slate-300">
          {t("channels.lead")}
        </p>
      </Reveal>

      {/* A destination can be wider than a phone, so the table scrolls inside its own box
          rather than pushing the page sideways. */}
      <div className="neu-scroll-thin mt-5 overflow-x-auto">
        <table className="w-full min-w-[36rem] border-collapse text-left">
          <thead>
            <tr>
              {["a", "b", "c"].map((col) => (
                <th
                  key={col}
                  scope="col"
                  className={`border-b pb-2.5 pr-5 font-mono text-[0.62rem] font-bold uppercase tracking-[0.16em] text-slate-500 dark:text-slate-400 ${RULE}`}
                >
                  {t(`channels.head.${col}`)}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {CHANNELS.map((destination, row) => (
              <tr key={destination}>
                <td
                  className={`border-b py-3 pr-5 align-top font-mono text-[0.78rem] font-semibold text-slate-800 dark:text-slate-100 ${RULE}`}
                >
                  {destination}
                </td>
                <td
                  className={`border-b py-3 pr-5 align-top text-sm text-slate-600 dark:text-slate-300 ${RULE}`}
                >
                  {t(`channels.rows.${row}.b`)}
                </td>
                <td
                  className={`border-b py-3 align-top text-sm text-slate-600 dark:text-slate-300 ${RULE}`}
                >
                  {t(`channels.rows.${row}.c`)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

    </Chapter>
  );
}

/* ------------------------------------------------------------------------------------ shell */

export function TechnicalRealtime() {
  const t = useTranslations("features.technical");
  const tr = useTranslations("features.technical.realtime");

  return (
    <TechnicalSectionShell
      id="realtime"
      icon={Radio}
      eyebrow={t("sections.realtime.eyebrow")}
      title={t("sections.realtime.title")}
    >
      <Reveal className="mt-8">
        <p className="max-w-3xl text-pretty text-base leading-relaxed text-slate-600 dark:text-slate-300 sm:text-lg">
          {tr("intro")}
        </p>
      </Reveal>

      {/* Plain anchors rather than state: every chapter is mounted, and a link the browser
          resolves itself keeps working while JavaScript is still loading. */}
      <Reveal className="mt-7">
        <nav aria-label={tr("toc.label")}>
          <ol className={`flex flex-col divide-y border-y ${RULE} divide-slate-300/70 dark:divide-slate-600/50`}>
            {REALTIME_BLOCKS.map((block, index) => (
              <li key={block}>
                <a
                  href={`#rt-${block}`}
                  className="group flex items-baseline gap-4 py-3 text-slate-600 transition-colors hover:text-indigo-600 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 dark:text-slate-300 dark:hover:text-indigo-400"
                >
                  <span
                    aria-hidden="true"
                    className="font-mono text-xs font-bold tabular-nums text-indigo-600 dark:text-indigo-400"
                  >
                    {String(index + 1).padStart(2, "0")}
                  </span>
                  <span className="text-pretty text-base font-semibold">
                    {tr(`toc.items.${block}`)}
                  </span>
                </a>
              </li>
            ))}
          </ol>
        </nav>
      </Reveal>

      <ProblemChapter />
      <WebSocketChapter />
      <StompChapter />
      <PubSubChapter />
      <SystemChapter />
    </TechnicalSectionShell>
  );
}
