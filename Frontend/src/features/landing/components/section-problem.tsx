"use client";

import type { ReactNode } from "react";
import { motion, useReducedMotion } from "framer-motion";
import { useTranslations } from "next-intl";
import { CornerDownRight } from "lucide-react";
import {
  RevealGroup,
  RevealItem,
  Section,
  SectionHeader,
} from "@/components/marketing/section-primitives";

export function SectionProblem() {
  const t = useTranslations("landing.problem");

  return (
    <Section>
      <SectionHeader eyebrow={t("eyebrow")} title={t("title")} lead={t("lead")} />

      <RevealGroup className="mt-14 grid gap-7 lg:mt-16 lg:grid-cols-2">
        <ProblemPanel
          label={t("leak.label")}
          title={t("leak.title")}
          body={t("leak.body")}
          answer={t("leak.answer")}
          visual={<CopiesVisual />}
        />
        <ProblemPanel
          label={t("drift.label")}
          title={t("drift.title")}
          body={t("drift.body")}
          answer={t("drift.answer")}
          visual={<DriftVisual />}
        />
      </RevealGroup>
    </Section>
  );
}

interface ProblemPanelProps {
  label: string;
  title: string;
  body: string;
  answer: string;
  visual: ReactNode;
}

function ProblemPanel({ label, title, body, answer, visual }: ProblemPanelProps) {
  return (
    <RevealItem className="h-full">
      <article className="neu-lift flex h-full flex-col rounded-3xl border-none p-7 sm:p-9">
        <span className="neu-pressed-sm inline-flex w-fit items-center rounded-full px-3.5 py-1.5 font-mono text-xs font-bold uppercase tracking-[0.2em] text-indigo-600 dark:text-indigo-400">
          {label}
        </span>
        <h3 className="mt-5 text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-100 sm:text-3xl">
          {title}
        </h3>
        <p className="mt-3 text-sm leading-relaxed text-slate-600 dark:text-slate-300 sm:text-base">{body}</p>

        {/* The demo sits in a well, so the raised card reads as the frame around it
            rather than as another slab of the same height. */}
        <div className="neu-pressed mt-8 mb-8 rounded-2xl px-5 py-6" aria-hidden="true">
          {visual}
        </div>

        <div className="neu-pressed-sm mt-auto flex items-start gap-3 rounded-2xl px-5 py-4">
          <CornerDownRight className="mt-0.5 size-4 shrink-0 text-indigo-600 dark:text-indigo-400" aria-hidden="true" />
          <p className="text-sm font-semibold leading-relaxed text-slate-800 dark:text-slate-200">{answer}</p>
        </div>
      </article>
    </RevealItem>
  );
}

function CopiesVisual() {
  const prefersReducedMotion = useReducedMotion();
  const ghosts = [1, 2, 3];

  return (
    <div className="relative h-28 w-full">
      {ghosts.map((depth) => (
        <motion.div
          key={depth}
          className="neu-raised-sm absolute left-0 top-2 h-24 w-40 rounded-2xl border-none"
          initial={{ x: 0, y: 0, opacity: 0.9 }}
          animate={
            prefersReducedMotion
              ? { x: depth * 46, y: depth * -5, opacity: 0.5 }
              : { x: [0, depth * 46], y: [0, depth * -5], opacity: [0.9, 0] }
          }
          transition={{
            duration: 3.6,
            ease: "easeOut",
            repeat: prefersReducedMotion ? 0 : Infinity,
            delay: depth * 0.5,
          }}
        />
      ))}

      <div className="neu-raised absolute left-0 top-2 flex h-24 w-40 flex-col justify-between rounded-2xl border-none p-3.5">
        <MiniWave />
        <span className="font-mono text-xs font-semibold text-indigo-600 dark:text-indigo-400">demo.wav</span>
      </div>
    </div>
  );
}

function MiniWave() {
  const heights = [40, 70, 100, 55, 85, 30, 65, 95, 45, 75, 35, 60];

  return (
    <div className="flex h-8 items-center gap-1">
      {heights.map((height, index) => (
        <span
          key={index}
          className="w-1 rounded-full bg-indigo-600 dark:bg-indigo-400"
          style={{ height: `${height}%` }}
        />
      ))}
    </div>
  );
}

function DriftVisual() {
  const prefersReducedMotion = useReducedMotion();
  const drifts = [0, 22, -16];

  return (
    <div className="flex h-28 w-full flex-col justify-center gap-7">
      {drifts.map((drift, index) => (
        <div key={index} className="neu-pressed-sm relative h-2 w-full rounded-full">
          <motion.span
            className="absolute -top-1 size-4 rounded-full bg-indigo-600 shadow-neu-raised-sm dark:bg-indigo-400"
            style={{ left: "45%" }}
            animate={
              prefersReducedMotion ? { x: drift } : { x: [0, drift, drift, 0, 0] }
            }
            transition={{
              duration: 4.5,
              times: [0, 0.35, 0.6, 0.75, 1],
              ease: "easeInOut",
              repeat: prefersReducedMotion ? 0 : Infinity,
            }}
          />
        </div>
      ))}
    </div>
  );
}
