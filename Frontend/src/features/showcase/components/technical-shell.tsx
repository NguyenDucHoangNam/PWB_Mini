"use client";

import type { ReactNode } from "react";
import type { LucideIcon } from "lucide-react";
import { PenLine } from "lucide-react";
import { useTranslations } from "next-intl";
import { Eyebrow, Reveal, SectionTitle } from "@/components/marketing/section-primitives";
import { TECHNICAL_SECTION_IDS } from "@/features/showcase/lib/technical-sections";
import { cn } from "@/lib/utils";

interface TechnicalSectionShellProps {
  id: string;
  icon: LucideIcon;
  title: string;
  eyebrow: string;
  children: ReactNode;
  className?: string;
}

export function TechnicalSectionShell({
  id,
  icon: Icon,
  title,
  eyebrow,
  children,
  className,
}: TechnicalSectionShellProps) {
  /* Read off the section list rather than passed in. The number also appears on the nav tab,
     where it is the tab's position, so a hand-written one here drifts the moment a section is
     added or dropped — which is exactly what happened when "modules" was removed. */
  const index = TECHNICAL_SECTION_IDS.indexOf(id) + 1;

  return (
    /* The panel holds no focusable content of its own, so it takes a tab stop — otherwise
       a keyboard reader leaves the tab bar and lands past everything they just switched to. */
    <section
      id={id}
      role="tabpanel"
      aria-labelledby={`${id}-tab`}
      tabIndex={0}
      className={cn(
        "neu-raised rounded-3xl bg-[#e0e5ec] p-6 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 dark:bg-[#1e222b] sm:p-10",
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
