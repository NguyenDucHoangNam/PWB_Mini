"use client";

import type { ReactNode } from "react";
import type { LucideIcon } from "lucide-react";
import { PenLine } from "lucide-react";
import { useTranslations } from "next-intl";
import { Eyebrow, Reveal, SectionTitle } from "@/components/marketing/section-primitives";
import { cn } from "@/lib/utils";

/* Clears the sticky header (h-20) plus the section nav resting at top-24, so an
   anchor jump never parks a heading underneath either of them. */
export const ANCHOR_OFFSET = "scroll-mt-[10.5rem]";

interface TechnicalSectionShellProps {
  id: string;
  index: number;
  icon: LucideIcon;
  title: string;
  eyebrow: string;
  children: ReactNode;
  className?: string;
}

export function TechnicalSectionShell({
  id,
  index,
  icon: Icon,
  title,
  eyebrow,
  children,
  className,
}: TechnicalSectionShellProps) {
  return (
    <section
      id={id}
      className={cn(
        "neu-raised rounded-3xl bg-[#e0e5ec] p-6 dark:bg-[#1e222b] sm:p-10",
        ANCHOR_OFFSET,
        className,
      )}
    >
      <Reveal>
        <div className="flex flex-wrap items-center gap-4">
          <span
            aria-hidden="true"
            className="neu-pressed flex size-11 shrink-0 items-center justify-center rounded-2xl bg-[#e0e5ec] font-mono text-xs font-bold tabular-nums text-indigo-600 dark:bg-[#1e222b] dark:text-indigo-400"
          >
            {String(index).padStart(2, "0")}
          </span>
          <Eyebrow>
            <Icon className="size-3.5" aria-hidden="true" />
            {eyebrow}
          </Eyebrow>
        </div>

        <SectionTitle>{title}</SectionTitle>
      </Reveal>

      {children}
    </section>
  );
}

/* Every content block on this page is still unwritten. Rather than collapse to nothing —
   which would make the skeleton impossible to judge — each one reserves its real footprint
   and says so, the same way StepMediaFrame marks a pending screenshot. */
export function TechnicalPlaceholder({
  label,
  className,
}: {
  label?: string;
  className?: string;
}) {
  const t = useTranslations("features.technical");

  return (
    <div
      className={cn(
        "neu-pressed flex flex-col items-center justify-center gap-3 rounded-3xl bg-[#e0e5ec] p-8 text-center dark:bg-[#1e222b]",
        className,
      )}
    >
      <span className="neu-raised-sm flex size-11 items-center justify-center rounded-2xl bg-[#e0e5ec] text-indigo-600 dark:bg-[#1e222b] dark:text-indigo-400">
        <PenLine className="size-4" aria-hidden="true" />
      </span>
      <span className="font-mono text-xs font-bold uppercase tracking-[0.18em] text-slate-500 dark:text-slate-400">
        {label ?? t("pending")}
      </span>
    </div>
  );
}
