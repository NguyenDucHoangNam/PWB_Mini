"use client";

import { useRef } from "react";
import { motion, useScroll, useSpring } from "framer-motion";
import { useTranslations } from "next-intl";
import { Eyebrow, Reveal, Section, SectionTitle } from "./section-primitives";

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
      <Reveal>
        <Eyebrow>{t("eyebrow")}</Eyebrow>
        <SectionTitle>{t("title")}</SectionTitle>
      </Reveal>

      <div ref={railRef} className="relative mt-14 lg:mt-16">
        {/* The rail threads behind the keycaps and fills as the section scrolls past. */}
        <span
          aria-hidden="true"
          className="absolute left-6 top-0 bottom-0 w-px bg-border sm:left-7"
        />
        <motion.span
          aria-hidden="true"
          className="absolute left-6 top-0 bottom-0 w-px origin-top bg-foreground sm:left-7"
          style={{ scaleY: railProgress }}
        />

        {STEP_KEYS.map((key, index) => (
          <Reveal key={key}>
            <div className="grid grid-cols-[3rem_1fr] gap-6 pb-12 last:pb-0 sm:grid-cols-[3.5rem_1fr] sm:gap-9">
              <div className="flex justify-center">
                <span
                  className="key-white flex h-16 w-9 items-end justify-center pb-2.5 font-mono text-xs font-semibold sm:h-20 sm:w-11"
                  aria-hidden="true"
                >
                  {String(index + 1).padStart(2, "0")}
                </span>
              </div>

              <div className="pt-1.5">
                <div className="flex flex-wrap items-baseline gap-x-4 gap-y-1">
                  <h3 className="text-xl font-semibold tracking-tight text-foreground sm:text-2xl">
                    {t(`${key}Title`)}
                  </h3>
                  <span className="font-mono text-xs uppercase tracking-[0.16em] text-muted-foreground">
                    {t(`${key}Meta`)}
                  </span>
                </div>
                <p className="mt-3 max-w-2xl text-sm leading-relaxed text-muted-foreground sm:text-base">
                  {t(`${key}Body`)}
                </p>
              </div>
            </div>
          </Reveal>
        ))}
      </div>
    </Section>
  );
}
