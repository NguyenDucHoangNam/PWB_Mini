"use client";

import type { ReactNode } from "react";
import { Info } from "lucide-react";
import { Reveal } from "@/components/marketing/section-primitives";
import { cn } from "@/lib/utils";

export interface StepFact {
  label: string;
  value: string;
}

interface WalkthroughStepProps {
  index: string;
  title: string;
  body: string;
  facts?: StepFact[];
  note?: string;
  media?: ReactNode;
  reversed?: boolean;
  children?: ReactNode;
  last?: boolean;
}

export function WalkthroughStep({
  index,
  title,
  body,
  facts,
  note,
  media,
  reversed = false,
  children,
  last = false,
}: WalkthroughStepProps) {
  return (
    <li className="relative grid grid-cols-[3rem_1fr] gap-6 sm:grid-cols-[3.5rem_1fr] sm:gap-9">
      {!last && (
        <span
          aria-hidden="true"
          className="absolute left-6 top-0 -bottom-12 w-px bg-slate-300 dark:bg-slate-700 sm:left-7 sm:-bottom-14"
        />
      )}

      <div className="relative flex justify-center">
        <span
          aria-hidden="true"
          className="neu-raised flex h-14 w-11 items-center justify-center rounded-2xl font-mono text-sm font-bold text-indigo-600 dark:text-indigo-400 bg-[#e0e5ec] dark:bg-[#1e222b]"
        >
          {index}
        </span>
      </div>

      <Reveal>
        <div
          className={cn(
            "grid gap-7 pt-1.5 lg:gap-12",
            media && "lg:grid-cols-2 lg:items-start",
          )}
        >
          <div className={cn(reversed && "lg:order-2")}>
            <h3 className="font-heading text-xl font-bold tracking-tight text-slate-900 sm:text-2xl dark:text-slate-100">
              {title}
            </h3>
            <p className="mt-3 max-w-2xl text-pretty text-sm font-medium leading-relaxed text-slate-600 sm:text-base dark:text-slate-300">
              {body}
            </p>

            {facts && facts.length > 0 && (
              <dl
                className={cn(
                  "neu-pressed mt-6 grid gap-2.5 rounded-2xl bg-[#e0e5ec] p-2.5 dark:bg-[#1e222b] border-none",
                  facts.length > 1 && "sm:grid-cols-2 lg:grid-cols-1",
                )}
              >
                {facts.map((fact) => (
                  <div key={fact.label} className="neu-raised-sm rounded-xl bg-[#e0e5ec] px-4 py-3 dark:bg-[#1e222b]">
                    <dt className="font-mono text-xs font-bold uppercase tracking-[0.16em] text-slate-500 dark:text-slate-400">
                      {fact.label}
                    </dt>
                    <dd className="mt-1 text-sm font-semibold leading-relaxed text-slate-900 dark:text-slate-100">{fact.value}</dd>
                  </div>
                ))}
              </dl>
            )}

            {note && (
              <p className="neu-pressed mt-6 flex max-w-2xl gap-3 rounded-2xl bg-[#e0e5ec] p-4 text-sm font-medium leading-relaxed text-slate-600 dark:bg-[#1e222b] dark:text-slate-300 border-none">
                <Info className="mt-0.5 size-4 shrink-0 text-indigo-600 dark:text-indigo-400" aria-hidden="true" />
                <span>{note}</span>
              </p>
            )}
          </div>

          {media && (
            <div className={cn("lg:self-center", reversed && "lg:order-1")}>{media}</div>
          )}
        </div>

        {children && <div className="mt-7">{children}</div>}
      </Reveal>
    </li>
  );
}

interface WalkthroughBranchProps {
  badge: string;
  title: string;
  body: string;
  facts: StepFact[];
  children?: ReactNode;
}

export function WalkthroughBranch({
  badge,
  title,
  body,
  facts,
  children,
}: WalkthroughBranchProps) {
  return (
    <div className="neu-raised flex flex-col gap-5 rounded-3xl bg-[#e0e5ec] p-6 dark:bg-[#1e222b] border-none">
      <div>
        <span className="neu-pressed-sm inline-flex items-center rounded-full px-3.5 py-1 font-mono text-xs font-bold uppercase tracking-[0.16em] text-indigo-600 dark:text-indigo-400">
          {badge}
        </span>
        <h4 className="mt-4 text-lg font-bold tracking-tight text-slate-900 dark:text-slate-100">{title}</h4>
        <p className="mt-2.5 text-sm font-medium leading-relaxed text-slate-600 dark:text-slate-300">{body}</p>
      </div>

      <dl className="neu-pressed flex flex-col gap-2.5 rounded-2xl bg-[#e0e5ec] p-4 dark:bg-[#1e222b] border-none">
        {facts.map((fact) => (
          <div key={fact.label} className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
            <dt className="min-w-28 font-mono text-xs font-bold uppercase tracking-[0.16em] text-slate-500 dark:text-slate-400">
              {fact.label}
            </dt>
            <dd className="flex-1 text-sm font-semibold text-slate-900 dark:text-slate-100">{fact.value}</dd>
          </div>
        ))}
      </dl>

      {children && <div className="mt-auto">{children}</div>}
    </div>
  );
}
