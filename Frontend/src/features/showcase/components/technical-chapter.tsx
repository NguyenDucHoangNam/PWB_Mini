"use client";

import type { ReactNode } from "react";
import { Reveal } from "@/components/marketing/section-primitives";

/* Shared furniture for the two long-form chapters on this page — the realtime layer and the
   event queue. Both are flat by exception: every other panel is moulded, and these two are not,
   because they are the parts whose job is to teach something the reader does not already know.
   Shadows on cards inside cards make a long explanation read as a pile of controls instead of a
   page of prose.

   Extracted the moment there were two of them. Keeping a private copy in each file is how the
   two would drift into looking like different websites. */

export const CARD = "rounded-2xl border border-slate-300/70 p-5 dark:border-slate-600/50";
export const RULE = "border-slate-300/70 dark:border-slate-600/50";

/** Numbered message keys for a fixed-length list. next-intl has no array lookup. */
export function ids(prefix: string, count: number) {
  return Array.from({ length: count }, (_, index) => `${prefix}.${index}`);
}

/* One numbered chapter. The number is the reader's place in the argument, so it belongs inside
   the heading rather than sitting beside it as decoration. */
export function Chapter({
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
        {/* scroll-mt keeps the heading clear of the sticky page header when the contents list
            jumps here. */}
        <div id={`${id}`} className="scroll-mt-28">
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

export function Prose({ children }: { children: ReactNode }) {
  return (
    <p className="max-w-3xl text-pretty text-base leading-relaxed text-slate-600 dark:text-slate-300">
      {children}
    </p>
  );
}

/* The chapter list that opens a long panel. Plain anchors rather than state: every chapter is
   mounted, so a link the browser resolves itself keeps working while JavaScript is loading. */
export function ChapterIndex({
  label,
  blocks,
  anchorPrefix,
  title,
}: {
  label: string;
  blocks: readonly string[];
  anchorPrefix: string;
  title: (block: string) => string;
}) {
  return (
    <nav aria-label={label}>
      <ol className={`flex flex-col divide-y border-y ${RULE} divide-slate-300/70 dark:divide-slate-600/50`}>
        {blocks.map((block, index) => (
          <li key={block}>
            <a
              href={`#${anchorPrefix}-${block}`}
              className="flex items-baseline gap-4 py-3 text-slate-600 transition-colors hover:text-indigo-600 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 dark:text-slate-300 dark:hover:text-indigo-400"
            >
              <span
                aria-hidden="true"
                className="font-mono text-xs font-bold tabular-nums text-indigo-600 dark:text-indigo-400"
              >
                {String(index + 1).padStart(2, "0")}
              </span>
              <span className="text-pretty text-base font-semibold">{title(block)}</span>
            </a>
          </li>
        ))}
      </ol>
    </nav>
  );
}

/* A numbered vertical walkthrough. Both chapters end with one: the realtime layer walks a play
   click through the system, the queue chapter walks a registration through it. */
export function StepRail({
  count,
  title,
  body,
}: {
  count: number;
  title: (index: number) => string;
  body: (index: number) => string;
}) {
  return (
    <>
      {Array.from({ length: count }, (_, index) => (
        <div key={index} className="flex gap-4 sm:gap-5">
          {/* The rail belongs to the spacer column, not to a border on the card, so it keeps
              running through the gap between two stages. */}
          <div className="flex flex-col items-center">
            <span
              className={`flex size-9 shrink-0 items-center justify-center rounded-full border font-mono text-xs font-bold tabular-nums text-indigo-600 dark:text-indigo-400 ${RULE}`}
            >
              {index + 1}
            </span>
            {index < count - 1 ? (
              <span
                aria-hidden="true"
                className="my-1 w-px grow bg-slate-300/70 dark:bg-slate-600/50"
              />
            ) : null}
          </div>

          <div className={index < count - 1 ? "pb-7" : undefined}>
            <h5 className="font-heading text-base font-bold tracking-tight text-slate-900 dark:text-slate-50">
              {title(index)}
            </h5>
            <p className="mt-2 max-w-3xl text-pretty text-sm leading-relaxed text-slate-600 dark:text-slate-300">
              {body(index)}
            </p>
          </div>
        </div>
      ))}
    </>
  );
}
