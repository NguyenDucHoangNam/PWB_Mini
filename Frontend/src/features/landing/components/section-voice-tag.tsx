"use client";

import { motion, useReducedMotion } from "framer-motion";
import { useTranslations } from "next-intl";
import {
  Eyebrow,
  Reveal,
  RevealGroup,
  RevealItem,
  Section,
  SectionLead,
  SectionTitle,
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
      <Reveal>
        <Eyebrow>{t("eyebrow")}</Eyebrow>
        <SectionTitle>{t("title")}</SectionTitle>
        <SectionLead>{t("lead")}</SectionLead>
      </Reveal>

      <div className="mt-14 grid items-start gap-12 lg:mt-16 lg:grid-cols-2 lg:gap-16">
        <RevealGroup>
          {POINT_KEYS.map((key, index) => (
            <RevealItem
              key={key}
              className="flex gap-5 border-b border-border py-6 first:pt-0 last:border-b-0"
            >
              <span className="mt-0.5 font-mono text-xs tabular-nums text-muted-foreground">
                {String(index + 1).padStart(2, "0")}
              </span>
              <div>
                <h3 className="text-lg font-semibold tracking-tight text-foreground">
                  {t(`${key}Title`)}
                </h3>
                <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
                  {t(`${key}Body`)}
                </p>
              </div>
            </RevealItem>
          ))}

          <RevealItem>
            <p className="mt-10 font-mono text-xs uppercase tracking-[0.2em] text-muted-foreground">
              {t("specTitle")}
            </p>
            <dl className="mt-4 grid grid-cols-2 gap-px overflow-hidden rounded-2xl border border-border bg-border">
              {SPEC_KEYS.map((key) => (
                <div key={key} className="bg-card p-5">
                  <dt className="font-mono text-xs uppercase tracking-[0.16em] text-muted-foreground">
                    {t(`${key}Label`)}
                  </dt>
                  <dd className="mt-2.5 text-xl font-semibold tracking-tight text-foreground">
                    {t(`${key}Value`)}
                  </dd>
                  <dd className="mt-1 font-mono text-xs text-muted-foreground">
                    {t(`${key}Note`)}
                  </dd>
                </div>
              ))}
            </dl>
          </RevealItem>
        </RevealGroup>

        <Reveal>
          <TagTimeline
            trackLabel={t("trackLabel")}
            trackStatus={t("trackStatus")}
            tagMarker={t("tagMarker")}
          />
        </Reveal>
      </div>
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
    <figure className="rounded-2xl border border-border bg-card p-5 sm:p-7">
      <figcaption className="flex items-center justify-between gap-4">
        <span className="font-mono text-sm text-foreground">{trackLabel}</span>
        <span className="inline-flex items-center gap-2 rounded-full border border-border bg-background px-3 py-1 font-mono text-xs uppercase tracking-[0.16em] text-muted-foreground">
          <span className="waveform" aria-hidden="true" data-state={prefersReducedMotion ? "paused" : undefined}>
            <span />
            <span />
            <span />
            <span />
            <span />
          </span>
          {trackStatus}
        </span>
      </figcaption>

      <div className="mt-7" aria-hidden="true">
        {/* Tag markers sit in their own lane above the waveform. */}
        <div className="relative h-7 w-full">
          {TAG_WINDOWS.map((start) => (
            <motion.span
              key={start}
              className="absolute top-0 -translate-x-1/2 rounded-md border border-foreground/25 bg-background px-2 py-1 font-mono text-xs tracking-[0.14em] text-foreground"
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

        <div className="@container relative h-32 w-full">
          {TAG_WINDOWS.map((start) => (
            <span
              key={start}
              className="absolute inset-y-0 w-px border-l border-dashed border-border"
              style={{ left: `${((start + TAG_WIDTH_BARS / 2) / BAR_COUNT) * 100}%` }}
            />
          ))}

          <BarRow className="bg-muted-foreground/30" />

          {/* The played portion is a second, solid copy revealed by an expanding clip. */}
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
              <BarRow className="bg-foreground" />
            </div>
          </motion.div>

          <motion.span
            className="absolute inset-y-0 w-px bg-foreground"
            initial={{ left: "0%" }}
            animate={prefersReducedMotion ? { left: "38%" } : { left: ["0%", "100%"] }}
            transition={{
              duration: PLAYHEAD_SECONDS,
              ease: "linear",
              repeat: prefersReducedMotion ? 0 : Infinity,
            }}
          />
        </div>

        <div className="mt-4 flex items-center justify-between border-t border-border pt-3 font-mono text-xs text-muted-foreground">
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
