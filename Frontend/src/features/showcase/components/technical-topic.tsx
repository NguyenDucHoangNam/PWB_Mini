"use client";

import { useTranslations } from "next-intl";
import { ArrowDown } from "lucide-react";
import { Reveal, RevealGroup, RevealItem } from "@/components/marketing/section-primitives";
import {
  TOPIC_QUESTIONS,
  type TopicSpec,
  type TopicVisual,
} from "@/features/showcase/lib/technical-topics";

/* Reads a fixed-length list of numbered message keys. next-intl has no array lookup, so the
   count lives in the spec and the keys are generated from it — which also means an entry
   added to one locale and forgotten in the other fails loudly rather than silently vanishing. */
function useList(t: ReturnType<typeof useTranslations>, prefix: string, count: number) {
  return Array.from({ length: count }, (_, index) => `${prefix}.${index}`);
}

function TopicFlow({ id, steps }: { id: string; steps: number }) {
  const t = useTranslations("features.technical.topics");
  const keys = useList(t, `${id}.flow`, steps);

  return (
    <RevealGroup className="mt-8 flex flex-col">
      {keys.map((key, index) => (
        <RevealItem key={key}>
          <div className="flex gap-4 sm:gap-5">
            {/* The rail is drawn by the spacer column rather than a border on the card, so it
                keeps running through the gap between two steps. */}
            <div className="flex flex-col items-center">
              <span className="neu-pressed flex size-9 shrink-0 items-center justify-center rounded-full bg-[#e0e5ec] font-mono text-xs font-bold tabular-nums text-indigo-600 dark:bg-[#1e222b] dark:text-indigo-400">
                {index + 1}
              </span>
              {index < steps - 1 ? (
                <span
                  aria-hidden="true"
                  className="my-1 w-px grow bg-slate-400/45 dark:bg-slate-500/45"
                />
              ) : null}
            </div>

            <div className={index < steps - 1 ? "pb-6" : undefined}>
              <h4 className="font-heading text-base font-bold tracking-tight text-slate-900 dark:text-slate-50">
                {t(`${key}.title`)}
              </h4>
              <p className="mt-1.5 text-pretty text-sm font-medium leading-relaxed text-slate-600 dark:text-slate-300">
                {t(`${key}.body`)}
              </p>
            </div>
          </div>
        </RevealItem>
      ))}
    </RevealGroup>
  );
}

function TopicLayers({
  id,
  layers,
  highlight,
}: {
  id: string;
  layers: number;
  highlight: number;
}) {
  const t = useTranslations("features.technical.topics");
  const keys = useList(t, `${id}.layers`, layers);

  return (
    <RevealGroup className="mt-8 flex flex-col items-stretch">
      {keys.map((key, index) => {
        const isHighlight = index === highlight;

        return (
          <RevealItem key={key}>
            <div
              className={[
                "rounded-2xl px-5 py-4",
                isHighlight
                  ? "neu-pressed bg-[#e0e5ec] dark:bg-[#1e222b]"
                  : "neu-raised-sm bg-[#e0e5ec] dark:bg-[#1e222b]",
              ].join(" ")}
            >
              <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
                <span
                  className={[
                    "font-heading text-base font-bold tracking-tight",
                    isHighlight
                      ? "text-indigo-600 dark:text-indigo-400"
                      : "text-slate-900 dark:text-slate-50",
                  ].join(" ")}
                >
                  {t(`${key}.title`)}
                </span>
                <span className="font-mono text-[0.68rem] font-bold uppercase tracking-[0.14em] text-slate-500 dark:text-slate-400">
                  {t(`${key}.role`)}
                </span>
              </div>
              <p className="mt-2 text-pretty text-sm font-medium leading-relaxed text-slate-600 dark:text-slate-300">
                {t(`${key}.body`)}
              </p>
            </div>

            {index < layers - 1 ? (
              <div className="flex justify-center py-2">
                <ArrowDown
                  aria-hidden="true"
                  className="size-4 text-slate-400 dark:text-slate-500"
                />
              </div>
            ) : null}
          </RevealItem>
        );
      })}
    </RevealGroup>
  );
}

function TopicCards({ id, cards }: { id: string; cards: number }) {
  const t = useTranslations("features.technical.topics");
  const keys = useList(t, `${id}.cards`, cards);

  return (
    <RevealGroup className="mt-8 grid gap-5 md:grid-cols-3">
      {keys.map((key) => (
        <RevealItem key={key} className="h-full">
          <article className="neu-lift flex h-full flex-col rounded-2xl bg-[#e0e5ec] p-5 dark:bg-[#1e222b]">
            <span className="font-mono text-[0.62rem] font-bold uppercase tracking-[0.16em] text-indigo-600 dark:text-indigo-400">
              {t(`${key}.tag`)}
            </span>
            <h4 className="mt-2.5 font-heading text-base font-bold tracking-tight text-slate-900 dark:text-slate-50">
              {t(`${key}.title`)}
            </h4>
            <p className="mt-2 text-pretty text-sm font-medium leading-relaxed text-slate-600 dark:text-slate-300">
              {t(`${key}.body`)}
            </p>
          </article>
        </RevealItem>
      ))}
    </RevealGroup>
  );
}

function TopicVisualBlock({ id, visual }: { id: string; visual: TopicVisual }) {
  if (visual.kind === "flow") return <TopicFlow id={id} steps={visual.steps} />;
  if (visual.kind === "layers")
    return <TopicLayers id={id} layers={visual.layers} highlight={visual.highlight} />;
  return <TopicCards id={id} cards={visual.cards} />;
}

function TopicTable({ id, rows }: { id: string; rows: number }) {
  const t = useTranslations("features.technical.topics");
  const keys = useList(t, `${id}.table.rows`, rows);

  return (
    <Reveal className="mt-8">
      <h4 className="font-heading text-base font-bold tracking-tight text-slate-900 dark:text-slate-50">
        {t(`${id}.table.title`)}
      </h4>

      {/* A three-column table cannot shrink below the width its longest cell needs, so it gets
          its own scroller rather than forcing the whole page sideways on a phone. */}
      <div className="neu-scroll-thin mt-4 overflow-x-auto">
        <table className="w-full min-w-[36rem] border-collapse text-left">
          <thead>
            <tr>
              {["a", "b", "c"].map((col) => (
                <th
                  key={col}
                  scope="col"
                  className="border-b border-slate-400/35 pb-2.5 pr-5 font-mono text-[0.62rem] font-bold uppercase tracking-[0.16em] text-slate-500 dark:border-slate-500/35 dark:text-slate-400"
                >
                  {t(`${id}.table.head.${col}`)}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {keys.map((key) => (
              <tr key={key}>
                <td className="border-b border-slate-400/20 py-3 pr-5 align-top text-sm font-semibold text-slate-800 dark:border-slate-500/20 dark:text-slate-100">
                  {t(`${key}.a`)}
                </td>
                <td className="border-b border-slate-400/20 py-3 pr-5 align-top text-sm font-medium text-slate-600 dark:border-slate-500/20 dark:text-slate-300">
                  {t(`${key}.b`)}
                </td>
                <td className="border-b border-slate-400/20 py-3 align-top text-sm font-medium text-slate-600 dark:border-slate-500/20 dark:text-slate-300">
                  {t(`${key}.c`)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Reveal>
  );
}

/* One topic, always the same four questions in the same order: what it is, why it has to
   exist, when it gets used, and how it is put together here. */
export function TechnicalTopic({ spec }: { spec: TopicSpec }) {
  const t = useTranslations("features.technical.topics");
  const { id, icon: Icon } = spec;

  return (
    <article
      id={`topic-${id}`}
      className="neu-lift rounded-3xl bg-[#e0e5ec] p-6 dark:bg-[#1e222b] sm:p-8"
    >
      <Reveal>
        <div className="flex flex-wrap items-center gap-4">
          <span
            aria-hidden="true"
            className="neu-pressed flex size-12 shrink-0 items-center justify-center rounded-2xl bg-[#e0e5ec] text-indigo-600 dark:bg-[#1e222b] dark:text-indigo-400"
          >
            <Icon className="size-5" />
          </span>
          <h3 className="font-heading text-xl font-bold tracking-tight text-slate-900 dark:text-slate-50 sm:text-2xl">
            {t(`${id}.title`)}
          </h3>
        </div>

        <p className="mt-5 max-w-3xl text-pretty text-base font-medium leading-relaxed text-slate-600 dark:text-slate-300">
          {t(`${id}.lead`)}
        </p>
      </Reveal>

      <RevealGroup className="mt-8 grid gap-6 lg:grid-cols-2">
        {TOPIC_QUESTIONS.map((question) => {
          const bullets = spec.bullets[question];

          return (
            <RevealItem key={question} className="h-full">
              <div className="neu-pressed flex h-full flex-col rounded-2xl bg-[#e0e5ec] p-5 dark:bg-[#1e222b]">
                <span className="font-mono text-[0.62rem] font-bold uppercase tracking-[0.18em] text-indigo-600 dark:text-indigo-400">
                  {t(`questions.${question}`)}
                </span>
                <p className="mt-3 text-pretty text-sm font-medium leading-relaxed text-slate-600 dark:text-slate-300">
                  {t(`${id}.${question}.body`)}
                </p>

                {bullets > 0 ? (
                  <ul className="mt-3.5 flex flex-col gap-2">
                    {Array.from({ length: bullets }, (_, index) => (
                      <li key={index} className="flex gap-2.5">
                        <span
                          aria-hidden="true"
                          className="mt-[0.45rem] size-1.5 shrink-0 rounded-full bg-indigo-600/70 dark:bg-indigo-400/70"
                        />
                        <span className="text-pretty text-sm font-medium leading-relaxed text-slate-600 dark:text-slate-300">
                          {t(`${id}.${question}.points.${index}`)}
                        </span>
                      </li>
                    ))}
                  </ul>
                ) : null}
              </div>
            </RevealItem>
          );
        })}
      </RevealGroup>

      <Reveal className="mt-9">
        <h4 className="font-heading text-base font-bold tracking-tight text-slate-900 dark:text-slate-50">
          {t(`${id}.visualTitle`)}
        </h4>
      </Reveal>
      <TopicVisualBlock id={id} visual={spec.visual} />

      {spec.rows > 0 ? <TopicTable id={id} rows={spec.rows} /> : null}
    </article>
  );
}
