"use client";

import { useTranslations } from "next-intl";
import { Reveal, RevealGroup, RevealItem } from "@/components/marketing/section-primitives";
import { OUTBOX_DIAGRAM } from "@/features/showcase/lib/technical-diagrams";
import {
  DUAL_WRITE_OPTIONS,
  FLOW_STEPS,
  KAFKA_IDEAS,
  LANES,
  OUTBOX_BLOCKS,
  RELAY_RULES,
  SLOW_JOBS,
} from "@/features/showcase/lib/technical-outbox-content";
import { CARD, Chapter, ChapterIndex, Prose, RULE, StepRail, ids } from "./technical-chapter";
import { TechnicalDiagram } from "./technical-diagram";

/* ---------------------------------------------------------------- 01 · why anything is queued */

function WaitingChapter() {
  const t = useTranslations("features.technical.outbox.waiting");

  return (
    <Chapter
      id="ob-waiting"
      step={1}
      eyebrow={t("eyebrow")}
      title={t("title")}
      lead={t("lead")}
    >
      <RevealGroup className="mt-7 grid gap-5 md:grid-cols-2">
        {ids("jobs", SLOW_JOBS).map((key) => (
          <RevealItem key={key} className="h-full">
            <div className={`flex h-full flex-col ${CARD}`}>
              <span className="font-mono text-[0.62rem] font-bold uppercase tracking-[0.16em] text-indigo-600 dark:text-indigo-400">
                {t(`${key}.cost`)}
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

/* ------------------------------------------------------------------------- 02 · what Kafka is */

function KafkaChapter() {
  const t = useTranslations("features.technical.outbox.kafka");

  return (
    <Chapter id="ob-kafka" step={2} eyebrow={t("eyebrow")} title={t("title")} lead={t("lead")}>
      <Reveal className="mt-7">
        <div className={`rounded-2xl border p-5 sm:p-6 ${RULE}`}>
          <p className="max-w-3xl text-pretty text-base font-semibold leading-relaxed text-slate-900 dark:text-slate-50">
            {t("notebook.title")}
          </p>
          <p className="mt-3 max-w-3xl text-pretty text-sm leading-relaxed text-slate-600 dark:text-slate-300">
            {t("notebook.body")}
          </p>
        </div>
      </Reveal>

      <RevealGroup className="mt-7 flex flex-col gap-4">
        {ids("ideas", KAFKA_IDEAS).map((key, index) => (
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

/* --------------------------------------------------------------------- 03 · the dual write */

function DualWriteChapter() {
  const t = useTranslations("features.technical.outbox.dualwrite");

  return (
    <Chapter id="ob-dualwrite" step={3} eyebrow={t("eyebrow")} title={t("title")} lead={t("lead")}>
      <Reveal className="mt-6">
        <Prose>{t("setup")}</Prose>
      </Reveal>

      {/* Two options, side by side, both wrong. Presenting them as a choice the reader has to
          make is what earns the pattern in the next chapter — otherwise the outbox reads as
          ceremony rather than the only way out. */}
      <Reveal className="mt-7">
        <div className={`grid gap-px overflow-hidden rounded-2xl border ${RULE} sm:grid-cols-2`}>
          {ids("options", DUAL_WRITE_OPTIONS).map((key) => (
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
              <p className="mt-3 text-pretty text-sm font-semibold leading-relaxed text-slate-700 dark:text-slate-200">
                {t(`${key}.result`)}
              </p>
            </div>
          ))}
        </div>
      </Reveal>

      <Reveal className="mt-7">
        <Prose>{t("closing")}</Prose>
      </Reveal>
    </Chapter>
  );
}

/* ------------------------------------------------------------------------- 04 · the outbox */

function OutboxChapter() {
  const t = useTranslations("features.technical.outbox.outbox");

  return (
    <Chapter id="ob-outbox" step={4} eyebrow={t("eyebrow")} title={t("title")} lead={t("lead")}>
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

      <Reveal className="mt-8">
        <h4 className="font-heading text-lg font-bold tracking-tight text-slate-900 dark:text-slate-50">
          {t("relay.title")}
        </h4>
        <p className="mt-3 max-w-3xl text-pretty text-base leading-relaxed text-slate-600 dark:text-slate-300">
          {t("relay.lead")}
        </p>
      </Reveal>

      <RevealGroup className="mt-6 grid gap-5 md:grid-cols-3">
        {ids("relay.rules", RELAY_RULES).map((key) => (
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
    </Chapter>
  );
}

/* ------------------------------------------------------------------------ 05 · what was built */

function SystemChapter() {
  const t = useTranslations("features.technical.outbox.system");

  return (
    <Chapter id="ob-system" step={5} eyebrow={t("eyebrow")} title={t("title")} lead={t("lead")}>
      <Reveal className="mt-7">
        <TechnicalDiagram spec={OUTBOX_DIAGRAM} flat />
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
        <StepRail
          count={FLOW_STEPS}
          title={(index) => t(`flow.steps.${index}.title`)}
          body={(index) => t(`flow.steps.${index}.body`)}
        />
      </RevealGroup>

      <Reveal className="mt-10">
        <h4 className="font-heading text-lg font-bold tracking-tight text-slate-900 dark:text-slate-50">
          {t("lanes.title")}
        </h4>
        <p className="mt-3 max-w-3xl text-pretty text-base leading-relaxed text-slate-600 dark:text-slate-300">
          {t("lanes.lead")}
        </p>
      </Reveal>

      <RevealGroup className="mt-6 grid gap-5 md:grid-cols-2">
        {LANES.map((topic, index) => (
          <RevealItem key={topic} className="h-full">
            <div className={`flex h-full flex-col ${CARD}`}>
              <span className="font-mono text-[0.72rem] font-semibold text-indigo-600 dark:text-indigo-400">
                {topic}
              </span>
              <h5 className="mt-2.5 font-heading text-base font-bold tracking-tight text-slate-900 dark:text-slate-50">
                {t(`lanes.items.${index}.title`)}
              </h5>
              <p className="mt-2 text-pretty text-sm leading-relaxed text-slate-600 dark:text-slate-300">
                {t(`lanes.items.${index}.body`)}
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

/* ------------------------------------------------------------------------------------ shell */

export function TechnicalOutbox() {
  const t = useTranslations("features.technical.outbox");

  return (
    <article id="topic-outbox" className={`rounded-3xl border p-6 sm:p-8 ${RULE}`}>
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
          blocks={OUTBOX_BLOCKS}
          anchorPrefix="ob"
          title={(block) => t(`toc.items.${block}`)}
        />
      </Reveal>

      <WaitingChapter />
      <KafkaChapter />
      <DualWriteChapter />
      <OutboxChapter />
      <SystemChapter />
    </article>
  );
}
