"use client";

import { Fragment } from "react";
import { motion, useReducedMotion } from "framer-motion";
import { useTranslations } from "next-intl";
import {
  Groove,
  NumberedPoint,
  Reveal,
  RevealGroup,
  RevealItem,
  Section,
  SectionHeader,
  pseudoRandom,
} from "@/components/marketing/section-primitives";

const POINT_KEYS = ["point1", "point2", "point3"] as const;
const SPEC_KEYS = ["spec1", "spec2", "spec3", "spec4"] as const;

const BAR_COUNT = 72;
const TAG_FIRST_BAR = 5;
const TAG_INTERVAL_BARS = 20;
const TAG_WIDTH_BARS = 4;
const DUCK_FACTOR = 0.3;
const PLAYHEAD_SECONDS = 11;
const TAG_PULSE_SECONDS = 0.6;

const TAG_WINDOWS = Array.from(
  { length: Math.ceil(BAR_COUNT / TAG_INTERVAL_BARS) },
  (_, index) => TAG_FIRST_BAR + index * TAG_INTERVAL_BARS,
).filter((start) => start + TAG_WIDTH_BARS <= BAR_COUNT);

const isTagged = (barIndex: number) =>
  TAG_WINDOWS.some((start) => barIndex >= start && barIndex < start + TAG_WIDTH_BARS);

/* The waveform itself shows the ducking: bars under a tag sit at a third of their height. */
const BAR_HEIGHTS = Array.from({ length: BAR_COUNT }, (_, index) => {
  const amplitude = 0.32 + pseudoRandom(index) * 0.68;
  return isTagged(index) ? amplitude * DUCK_FACTOR : amplitude;
});

export function SectionVoiceTag() {
  const t = useTranslations("landing.voiceTag");

  return (
    <Section>
      <SectionHeader eyebrow={t("eyebrow")} title={t("title")} lead={t("lead")} />

      <div className="mt-14 grid items-start gap-12 lg:mt-16 lg:grid-cols-[minmax(0,0.85fr)_minmax(0,1fr)] lg:gap-16">
        <RevealGroup>
          {/* Fragment, not a wrapper div: framer-motion only propagates variants through
              motion children, so a plain element here would strand the reveal. */}
          {POINT_KEYS.map((key, index) => (
            <Fragment key={key}>
              {index > 0 && <Groove className="my-7" />}
              <NumberedPoint
                index={index + 1}
                title={t(`${key}Title`)}
                body={t(`${key}Body`)}
              />
            </Fragment>
          ))}
        </RevealGroup>

        {/* Sticks while the points scroll past, so the waveform stays on screen for the
            paragraph that describes it. */}
        <Reveal className="lg:sticky lg:top-28">
          <TagTimeline
            trackLabel={t("trackLabel")}
            trackStatus={t("trackStatus")}
            tagMarker={t("tagMarker")}
          />
        </Reveal>
      </div>

      <RevealGroup className="mt-16">
        <RevealItem>
          <p className="font-mono text-xs font-bold uppercase tracking-[0.2em] text-indigo-600 dark:text-indigo-400">
            {t("specTitle")}
          </p>
        </RevealItem>
        <dl className="mt-5 grid grid-cols-2 gap-5 lg:grid-cols-4">
          {SPEC_KEYS.map((key) => (
            <RevealItem key={key} className="h-full">
              <div className="neu-raised-sm flex h-full flex-col rounded-2xl p-5">
                <dt className="font-mono text-xs font-semibold uppercase tracking-[0.16em] text-slate-500 dark:text-slate-400">
                  {t(`${key}Label`)}
                </dt>
                <dd className="mt-2.5 text-xl font-bold tracking-tight text-slate-900 dark:text-slate-100">
                  {t(`${key}Value`)}
                </dd>
                <dd className="mt-1 font-mono text-xs text-slate-600 dark:text-slate-400">
                  {t(`${key}Note`)}
                </dd>
              </div>
            </RevealItem>
          ))}
        </dl>
      </RevealGroup>
    </Section>
  );
}

interface TagTimelineProps {
  trackLabel: string;
  trackStatus: string;
  tagMarker: string;
}

function TagTimeline({ trackLabel, trackStatus, tagMarker }: TagTimelineProps) {
  const prefersReducedMotion = useReducedMotion();

  return (
    <figure className="neu-raised-lg rounded-3xl border-none p-6 sm:p-8">
      <figcaption className="flex items-center justify-between gap-4">
        <span className="font-mono text-sm font-bold text-slate-900 dark:text-slate-100">{trackLabel}</span>
        <span className="neu-pressed-sm inline-flex items-center gap-2 rounded-full px-4 py-1.5 font-mono text-xs font-semibold uppercase tracking-[0.16em] text-slate-700 dark:text-slate-300">
          <span className="waveform" aria-hidden="true" data-state={prefersReducedMotion ? "paused" : undefined}>
            <span className="bg-indigo-600 dark:bg-indigo-400" />
            <span className="bg-indigo-600 dark:bg-indigo-400" />
            <span className="bg-indigo-600 dark:bg-indigo-400" />
            <span className="bg-indigo-600 dark:bg-indigo-400" />
            <span className="bg-indigo-600 dark:bg-indigo-400" />
          </span>
          {trackStatus}
        </span>
      </figcaption>

      <div className="mt-7" aria-hidden="true">
        <div className="relative h-7 w-full">
          {TAG_WINDOWS.map((start) => (
            <motion.span
              key={start}
              className="absolute top-0 -translate-x-1/2 rounded-xl bg-indigo-600 px-3 py-1 font-mono text-xs font-bold tracking-[0.14em] text-white shadow-neu-raised-sm dark:bg-indigo-500"
              style={{ left: `${((start + TAG_WIDTH_BARS / 2) / BAR_COUNT) * 100}%` }}
              initial={{ opacity: 0.4 }}
              animate={prefersReducedMotion ? { opacity: 0.7 } : { opacity: [0.4, 1, 0.4] }}
              transition={{
                duration: TAG_PULSE_SECONDS,
                repeat: prefersReducedMotion ? 0 : Infinity,
                repeatDelay: PLAYHEAD_SECONDS - TAG_PULSE_SECONDS,
                delay: (start / BAR_COUNT) * PLAYHEAD_SECONDS,
              }}
            >
              {tagMarker}
            </motion.span>
          ))}
        </div>

        {/* The waveform is cut into the card rather than sitting on it — the deepest
            surface on the page, which is what makes the raised card read as a device. */}
        <div className="neu-pressed relative mt-1 h-36 w-full rounded-2xl p-4">
          <div className="@container relative h-full w-full">
            {TAG_WINDOWS.map((start) => (
              <span
                key={start}
                className="neu-groove-v absolute inset-y-3 opacity-70"
                style={{ left: `${((start + TAG_WIDTH_BARS / 2) / BAR_COUNT) * 100}%` }}
              />
            ))}

            <BarRow className="bg-slate-400/30 dark:bg-slate-600/30" />

            <motion.div
              className="absolute inset-y-0 left-0 overflow-hidden"
              initial={{ width: "0%" }}
              animate={prefersReducedMotion ? { width: "38%" } : { width: ["0%", "100%"] }}
              transition={{
                duration: PLAYHEAD_SECONDS,
                ease: "linear",
                repeat: prefersReducedMotion ? 0 : Infinity,
              }}
            >
              <div className="absolute inset-y-0 left-0 w-[100cqw]">
                <BarRow className="bg-indigo-600 dark:bg-indigo-400" />
              </div>
            </motion.div>

            <motion.span
              className="absolute inset-y-2 w-1 rounded-full bg-indigo-600 shadow-neu-raised-sm dark:bg-indigo-400"
              initial={{ left: "0%" }}
              animate={prefersReducedMotion ? { left: "38%" } : { left: ["0%", "100%"] }}
              transition={{
                duration: PLAYHEAD_SECONDS,
                ease: "linear",
                repeat: prefersReducedMotion ? 0 : Infinity,
              }}
            />
          </div>
        </div>

        <div className="mt-5 flex items-center justify-between font-mono text-xs font-semibold text-slate-500 dark:text-slate-400">
          <span>0:00</span>
          <span>0:30</span>
          <span>1:00</span>
          <span>1:30</span>
        </div>
      </div>
    </figure>
  );
}

function BarRow({ className }: { className: string }) {
  return (
    <div className="absolute inset-0 flex items-center gap-px">
      {BAR_HEIGHTS.map((height, index) => (
        <span
          key={index}
          className={`flex-1 rounded-full ${className}`}
          style={{ height: `${height * 100}%` }}
        />
      ))}
    </div>
  );
}
