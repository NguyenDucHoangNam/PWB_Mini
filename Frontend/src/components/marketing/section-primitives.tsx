"use client";

import type { ReactNode } from "react";
import { motion, type Variants } from "framer-motion";
import { cn } from "@/lib/utils";

const VIEWPORT = { once: true, margin: "-90px" } as const;

export const fadeUp: Variants = {
  hidden: { opacity: 0, y: 26 },
  visible: {
    opacity: 1,
    y: 0,
    transition: { duration: 0.6, ease: [0.16, 1, 0.3, 1] },
  },
};

export const staggerChildren: Variants = {
  hidden: {},
  visible: { transition: { staggerChildren: 0.09 } },
};

export function Reveal({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <motion.div
      className={className}
      variants={fadeUp}
      initial="hidden"
      whileInView="visible"
      viewport={VIEWPORT}
    >
      {children}
    </motion.div>
  );
}

export function RevealGroup({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <motion.div
      className={className}
      variants={staggerChildren}
      initial="hidden"
      whileInView="visible"
      viewport={VIEWPORT}
    >
      {children}
    </motion.div>
  );
}

export function RevealItem({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <motion.div className={className} variants={fadeUp}>
      {children}
    </motion.div>
  );
}

/* One surface colour runs the whole page, so a section cannot be marked off by its
   background — only by depth. "trough" presses the band into the sheet; alternating it
   with "base" is what stops eight stacked sections reading as one endless plate. */
export function Section({
  children,
  className,
  id,
  tone = "base",
  width = "default",
}: {
  children: ReactNode;
  className?: string;
  id?: string;
  tone?: "base" | "trough";
  width?: "default" | "wide";
}) {
  return (
    <section
      id={id}
      className={cn(
        "relative w-full overflow-hidden border-none bg-[var(--neu-surface)] px-5 py-24 transition-colors sm:px-8 sm:py-28 md:py-32",
        tone === "trough" && "neu-trough",
        className,
      )}
    >
      <div className={cn("mx-auto w-full", width === "wide" ? "max-w-7xl" : "max-w-6xl")}>
        {children}
      </div>
    </section>
  );
}

export function Eyebrow({
  children,
  className,
  variant = "bar",
}: {
  children: ReactNode;
  className?: string;
  variant?: "bar" | "chip";
}) {
  if (variant === "chip") {
    return (
      <span
        className={cn(
          "neu-pressed-sm inline-flex items-center gap-2.5 rounded-full px-4 py-2 font-mono text-xs font-bold uppercase tracking-[0.22em] text-indigo-600 dark:text-indigo-400",
          className,
        )}
      >
        <span aria-hidden="true" className="size-1.5 shrink-0 rounded-full bg-indigo-600 dark:bg-indigo-400" />
        {children}
      </span>
    );
  }

  return (
    <span
      className={cn(
        "inline-flex items-center gap-3 font-mono text-xs font-bold uppercase tracking-[0.22em] text-indigo-600 dark:text-indigo-400",
        className,
      )}
    >
      <span aria-hidden="true" className="h-0.5 w-7 bg-indigo-600 dark:bg-indigo-400" />
      {children}
    </span>
  );
}

export function SectionTitle({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <h2
      className={cn(
        "mt-5 max-w-3xl text-balance font-heading text-3xl font-bold tracking-tight text-slate-900 dark:text-slate-100 sm:text-4xl md:text-5xl",
        className,
      )}
    >
      {children}
    </h2>
  );
}

export function SectionLead({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <p
      className={cn(
        "mt-5 max-w-2xl text-pretty text-base leading-relaxed text-slate-600 dark:text-slate-300 sm:text-lg",
        className,
      )}
    >
      {children}
    </p>
  );
}

/* The eyebrow / title / lead block was hand-assembled in every section, which is how
   they drifted apart. One component, one alignment switch. */
export function SectionHeader({
  eyebrow,
  title,
  lead,
  align = "start",
  className,
}: {
  eyebrow: ReactNode;
  title: ReactNode;
  lead?: ReactNode;
  align?: "start" | "center";
  className?: string;
}) {
  const centered = align === "center";

  return (
    <Reveal className={cn("relative", centered && "flex flex-col items-center text-center", className)}>
      <Eyebrow variant="chip">{eyebrow}</Eyebrow>
      <SectionTitle className={centered ? "mx-auto" : undefined}>{title}</SectionTitle>
      {lead ? <SectionLead className={centered ? "mx-auto" : undefined}>{lead}</SectionLead> : null}
    </Reveal>
  );
}

export function Groove({ className }: { className?: string }) {
  return <span aria-hidden="true" className={cn("neu-groove block w-full", className)} />;
}

/* Shared by the two deep-dive sections so their point lists cannot drift apart. */
export function NumberedPoint({
  index,
  title,
  body,
  className,
}: {
  index: number;
  title: string;
  body: string;
  className?: string;
}) {
  return (
    <RevealItem className={cn("h-full", className)}>
      <div className="flex h-full gap-5">
        <span className="neu-pressed-sm mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-full font-mono text-xs font-bold tabular-nums text-indigo-600 dark:text-indigo-400">
          {String(index).padStart(2, "0")}
        </span>
        <div>
          <h3 className="text-lg font-bold tracking-tight text-slate-900 dark:text-slate-100">{title}</h3>
          <p className="mt-2 text-sm leading-relaxed text-slate-600 dark:text-slate-300">{body}</p>
        </div>
      </div>
    </RevealItem>
  );
}

export function pseudoRandom(seed: number): number {
  const x = Math.sin(seed * 127.1 + 311.7) * 43758.5453;
  return x - Math.floor(x);
}
