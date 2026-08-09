"use client";

import { useRef } from "react";
import { motion, useScroll, useSpring } from "framer-motion";
import { useTranslations } from "next-intl";
import { RevealGroup, RevealItem, Section, SectionHeader } from "@/components/marketing/section-primitives";

const STEP_KEYS = ["step1", "step2", "step3", "step4"] as const;

export function SectionWorkflow() {
  const t = useTranslations("landing.workflow");
  const railRef = useRef<HTMLDivElement>(null);

  const { scrollYProgress } = useScroll({
    target: railRef,
    offset: ["start 75%", "end 65%"],
  });
  const railProgress = useSpring(scrollYProgress, {
    stiffness: 90,
    damping: 24,
    restDelta: 0.001,
  });

  return (
    <Section>
      <SectionHeader eyebrow={t("eyebrow")} title={t("title")} />

      {/* Four steps read as a sequence when they run left to right, so the rail turns
          horizontal once there is room for it and falls back to the vertical spine on
          narrow screens. Both are fed by the same scroll progress. */}
      <div ref={railRef} className="relative mt-14 lg:mt-20">
        <div className="relative hidden lg:block">
          <div className="neu-groove absolute inset-x-0 top-7 -translate-y-1/2" aria-hidden="true" />
          <motion.div
            aria-hidden="true"
            className="absolute inset-x-0 top-7 h-0.5 origin-left -translate-y-1/2 rounded-full bg-indigo-600 dark:bg-indigo-400"
            style={{ scaleX: railProgress }}
          />

          <RevealGroup className="relative grid grid-cols-4 gap-8">
            {STEP_KEYS.map((key, index) => (
              <RevealItem key={key} className="h-full">
                <div className="flex h-full flex-col">
                  <span
                    className="neu-raised flex size-14 items-center justify-center rounded-2xl font-mono text-sm font-bold text-indigo-600 dark:text-indigo-400"
                    aria-hidden="true"
                  >
                    {String(index + 1).padStart(2, "0")}
                  </span>
                  <StepBody
                    title={t(`${key}Title`)}
                    meta={t(`${key}Meta`)}
                    body={t(`${key}Body`)}
                    className="mt-7"
                  />
                </div>
              </RevealItem>
            ))}
          </RevealGroup>
        </div>

        <div className="relative lg:hidden">
          <span aria-hidden="true" className="neu-groove-v absolute bottom-0 left-7 top-0 h-auto" />
          <motion.span
            aria-hidden="true"
            className="absolute bottom-0 left-7 top-0 w-0.5 origin-top rounded-full bg-indigo-600 dark:bg-indigo-400"
            style={{ scaleY: railProgress }}
          />

          <RevealGroup className="relative">
            {STEP_KEYS.map((key, index) => (
              <RevealItem key={key}>
                <div className="grid grid-cols-[3.5rem_1fr] gap-6 pb-12 last:pb-0">
                  <span
                    className="neu-raised flex size-14 items-center justify-center rounded-2xl font-mono text-sm font-bold text-indigo-600 dark:text-indigo-400"
                    aria-hidden="true"
                  >
                    {String(index + 1).padStart(2, "0")}
                  </span>
                  <StepBody
                    title={t(`${key}Title`)}
                    meta={t(`${key}Meta`)}
                    body={t(`${key}Body`)}
                    className="pt-1"
                  />
                </div>
              </RevealItem>
            ))}
          </RevealGroup>
        </div>
      </div>
    </Section>
  );
}

function StepBody({
  title,
  meta,
  body,
  className,
}: {
  title: string;
  meta: string;
  body: string;
  className?: string;
}) {
  return (
    <div className={className}>
      <h3 className="text-xl font-bold tracking-tight text-slate-900 dark:text-slate-100">{title}</h3>
      <span className="mt-2 inline-block font-mono text-xs font-semibold uppercase tracking-[0.16em] text-indigo-600 dark:text-indigo-400">
        {meta}
      </span>
      <p className="mt-3 text-sm leading-relaxed text-slate-600 dark:text-slate-300">{body}</p>
    </div>
  );
}
