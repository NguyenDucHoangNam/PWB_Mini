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
  /** Printed in the rail. Two digits keeps the badges the same width all the way down. */
  index: string;
  title: string;
  body: string;
  facts?: StepFact[];
  note?: string;
  /** The illustration, shown beside the text on wide screens and below it on narrow ones. */
  media?: ReactNode;
  /** Puts the illustration on the left, so consecutive steps zig-zag instead of marching. */
  reversed?: boolean;
  /** A block that needs the full width under the text — the fork's two branches. */
  children?: ReactNode;
  /** The connector line stops at the last badge instead of running off the end of the list. */
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
    /* Same rail geometry as the workflow steps on the landing page: a 3rem / 3.5rem marker column
       with the line running through its centre, so both pages walk the reader down the same spine. */
    <li className="relative grid grid-cols-[3rem_1fr] gap-6 sm:grid-cols-[3.5rem_1fr] sm:gap-9">
      {/* Overshoots into the gap below, which keeps the line unbroken between steps. */}
      {!last && (
        <span
          aria-hidden="true"
          className="absolute left-6 top-0 -bottom-12 w-px bg-border sm:left-7 sm:-bottom-14"
        />
      )}

      <div className="relative flex justify-center">
        <span
          aria-hidden="true"
          className="key-white flex h-16 w-9 items-end justify-center pb-2.5 font-mono text-xs font-semibold sm:h-20 sm:w-11"
        >
          {index}
        </span>
      </div>

      <Reveal>
        <div
          className={cn(
            "grid gap-7 pt-1.5 lg:gap-12",
            // Top-aligned, not centred: the title has to stay level with its keycap, or a short step
            // leaves the number stranded above the heading it belongs to.
            media && "lg:grid-cols-2 lg:items-start",
          )}
        >
          <div className={cn(reversed && "lg:order-2")}>
            <h3 className="font-heading text-xl font-semibold tracking-tight text-foreground sm:text-2xl">
              {title}
            </h3>
            <p className="mt-3 max-w-2xl text-pretty text-sm leading-relaxed text-muted-foreground sm:text-base">
              {body}
            </p>

            {facts && facts.length > 0 && (
              <dl
                className={cn(
                  "mt-6 grid gap-px overflow-hidden rounded-xl border border-border bg-border",
                  // Two across while the step owns the full width; back to one once the text is in
                  // a half-width column, where two would leave the values shredded.
                  facts.length > 1 && "sm:grid-cols-2 lg:grid-cols-1",
                )}
              >
                {facts.map((fact) => (
                  <div key={fact.label} className="bg-card px-4 py-3.5">
                    <dt className="font-mono text-[0.68rem] uppercase tracking-[0.16em] text-muted-foreground">
                      {fact.label}
                    </dt>
                    <dd className="mt-1.5 text-sm leading-relaxed text-foreground">{fact.value}</dd>
                  </div>
                ))}
              </dl>
            )}

            {note && (
              <p className="mt-6 flex max-w-2xl gap-3 rounded-xl border border-border bg-muted/50 p-4 text-sm leading-relaxed text-muted-foreground">
                <Info className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
                <span>{note}</span>
              </p>
            )}
          </div>

          {/* The illustration still centres itself against a text block taller than it. */}
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
  /** Short marker such as "A · TTS" — the letter is what ties it to the fork's instruction. */
  badge: string;
  title: string;
  body: string;
  facts: StepFact[];
  children?: ReactNode;
}

/** One side of a fork: two ways to reach the same result, only one of which needs doing. */
export function WalkthroughBranch({
  badge,
  title,
  body,
  facts,
  children,
}: WalkthroughBranchProps) {
  return (
    <div className="flex flex-col gap-5 rounded-2xl border border-border bg-card p-5 sm:p-6">
      <div>
        <span className="inline-flex items-center rounded-full border border-foreground/20 bg-background px-3 py-1 font-mono text-[0.68rem] uppercase tracking-[0.16em] text-foreground">
          {badge}
        </span>
        <h4 className="mt-4 text-lg font-semibold tracking-tight text-foreground">{title}</h4>
        <p className="mt-2.5 text-sm leading-relaxed text-muted-foreground">{body}</p>
      </div>

      <dl className="flex flex-col gap-2.5 border-t border-border pt-4">
        {facts.map((fact) => (
          <div key={fact.label} className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
            <dt className="min-w-28 font-mono text-[0.68rem] uppercase tracking-[0.16em] text-muted-foreground">
              {fact.label}
            </dt>
            <dd className="flex-1 text-sm text-foreground">{fact.value}</dd>
          </div>
        ))}
      </dl>

      {children && <div className="mt-auto">{children}</div>}
    </div>
  );
}
