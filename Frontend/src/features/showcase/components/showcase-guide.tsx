"use client";

import { useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { ListMusic, Mic, Radio } from "lucide-react";
import { useTranslations } from "next-intl";
import {
  Eyebrow,
  Reveal,
  Section,
  SectionLead,
  SectionTitle,
} from "@/components/marketing/section-primitives";
import { cn } from "@/lib/utils";

const MODULES = [
  { key: "library", icon: ListMusic, index: "01" },
  { key: "voiceTag", icon: Mic, index: "02" },
  { key: "liveRoom", icon: Radio, index: "03" },
] as const;

const STEP_KEYS = ["step1", "step2", "step3", "step4"] as const;
const TIP_KEYS = ["tip1", "tip2", "tip3"] as const;

type ModuleKey = (typeof MODULES)[number]["key"];

export function ShowcaseGuide() {
  const t = useTranslations("features.guide");
  const [active, setActive] = useState<ModuleKey>("library");

  return (
    <Section id="guide" tone="raised" className="scroll-mt-20">
      <Reveal>
        <Eyebrow>{t("eyebrow")}</Eyebrow>
        <SectionTitle>{t("title")}</SectionTitle>
        <SectionLead>{t("lead")}</SectionLead>
      </Reveal>

      <div className="mt-14 grid gap-8 lg:mt-16 lg:grid-cols-[minmax(0,17rem)_1fr] lg:gap-12">
        <Reveal>
          {/* Horizontal rail on small screens, a stacked list once there is room for it. */}
          <div
            role="tablist"
            aria-label={t("title")}
            className="-mx-5 flex gap-2 overflow-x-auto px-5 pb-2 lg:mx-0 lg:flex-col lg:overflow-visible lg:px-0 lg:pb-0"
          >
            {MODULES.map(({ key, icon: Icon, index }) => {
              const selected = key === active;
              return (
                <button
                  key={key}
                  type="button"
                  role="tab"
                  aria-selected={selected}
                  aria-controls={`guide-panel-${key}`}
                  onClick={() => setActive(key)}
                  className={cn(
                    "group flex shrink-0 items-center gap-3 rounded-xl border px-4 py-3.5 text-left beat-16th transition-colors lg:w-full",
                    selected
                      ? "border-foreground/25 bg-card"
                      : "border-transparent bg-transparent hover:border-border hover:bg-card/60",
                  )}
                >
                  <span
                    className={cn(
                      "flex size-9 shrink-0 items-center justify-center rounded-lg border beat-16th transition-colors",
                      selected
                        ? "border-foreground/20 bg-foreground text-background"
                        : "border-border bg-background text-muted-foreground",
                    )}
                  >
                    <Icon className="size-4" aria-hidden="true" />
                  </span>
                  <span className="min-w-0">
                    <span
                      className={cn(
                        "block text-sm font-semibold tracking-tight",
                        selected ? "text-foreground" : "text-muted-foreground",
                      )}
                    >
                      {t(`${key}.title`)}
                    </span>
                    <span className="mt-0.5 block font-mono text-[0.7rem] uppercase tracking-[0.16em] text-muted-foreground/70">
                      {index} · {t(`${key}.meta`)}
                    </span>
                  </span>
                </button>
              );
            })}
          </div>
        </Reveal>

        <div className="min-h-[30rem]">
          <AnimatePresence mode="wait">
            <motion.div
              key={active}
              id={`guide-panel-${active}`}
              role="tabpanel"
              initial={{ opacity: 0, y: 14 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -8 }}
              transition={{ duration: 0.28, ease: [0.16, 1, 0.3, 1] }}
              className="rounded-2xl border border-border bg-card p-6 sm:p-9"
            >
              <p className="max-w-2xl text-pretty text-base leading-relaxed text-foreground sm:text-lg">
                {t(`${active}.summary`)}
              </p>

              <ol className="relative mt-9 border-l border-border pl-7 sm:pl-9">
                {STEP_KEYS.map((stepKey, i) => (
                  <li key={stepKey} className="relative pb-8 last:pb-0">
                    <span
                      aria-hidden="true"
                      className="absolute -left-[calc(1.75rem+1px)] top-0.5 flex size-6 items-center justify-center rounded-full border border-border bg-background font-mono text-[0.65rem] font-semibold text-muted-foreground sm:-left-[calc(2.25rem+1px)] sm:size-7 sm:text-[0.7rem]"
                    >
                      {String(i + 1).padStart(2, "0")}
                    </span>
                    <h3 className="text-base font-semibold tracking-tight text-foreground sm:text-lg">
                      {t(`${active}.${stepKey}Title`)}
                    </h3>
                    <p className="mt-2 max-w-2xl text-sm leading-relaxed text-muted-foreground">
                      {t(`${active}.${stepKey}Body`)}
                    </p>
                  </li>
                ))}
              </ol>

              <div className="mt-9 rounded-xl border border-border bg-muted/50 p-5 sm:p-6">
                <span className="font-mono text-[0.7rem] uppercase tracking-[0.2em] text-muted-foreground">
                  {t("tipsTitle")}
                </span>
                <ul className="mt-4 space-y-3">
                  {TIP_KEYS.map((tipKey) => (
                    <li
                      key={tipKey}
                      className="flex gap-3 text-sm leading-relaxed text-muted-foreground"
                    >
                      <span
                        aria-hidden="true"
                        className="mt-2 size-1 shrink-0 rounded-full bg-muted-foreground/60"
                      />
                      {t(`${active}.${tipKey}`)}
                    </li>
                  ))}
                </ul>
              </div>
            </motion.div>
          </AnimatePresence>
        </div>
      </div>
    </Section>
  );
}
