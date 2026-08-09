"use client";

import { motion, useReducedMotion } from "framer-motion";
import { useTranslations } from "next-intl";
import { Mic, MicOff, Play } from "lucide-react";
import {
  Eyebrow,
  Reveal,
  RevealGroup,
  RevealItem,
  Section,
  SectionLead,
  SectionTitle,
} from "@/components/marketing/section-primitives";

const POINT_KEYS = ["point1", "point2", "point3"] as const;

const ROOM_CODE = "A3B-X7K";
const LOOP_SECONDS = 9;
const COMMENT_POSITION = 0.55;
const PLAYHEAD_END = 0.78;
/* The playhead sweeps to PLAYHEAD_END over the loop, so it reaches the pin at this fraction. */
const COMMENT_TIME = COMMENT_POSITION / PLAYHEAD_END;

const PARTICIPANTS = [
  { initials: "NH", muted: false },
  { initials: "TL", muted: true },
  { initials: "QA", muted: false },
  { initials: "DM", muted: true },
] as const;

export function SectionLiveRoom() {
  const t = useTranslations("landing.liveRoom");

  return (
    <Section tone="raised">
      <Reveal>
        <Eyebrow>{t("eyebrow")}</Eyebrow>
        <SectionTitle>{t("title")}</SectionTitle>
        <SectionLead>{t("lead")}</SectionLead>
      </Reveal>

      <div className="mt-14 grid items-start gap-12 lg:mt-16 lg:grid-cols-2 lg:gap-16">
        <Reveal className="lg:order-last">
          <RoomConsole
            roomCodeLabel={t("roomCodeLabel")}
            liveLabel={t("liveLabel")}
            syncedLabel={t("syncedLabel")}
            listenersLabel={t("listenersLabel")}
            commentSample={t("commentSample")}
            hostLabel={t("hostLabel")}
          />
        </Reveal>

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
        </RevealGroup>
      </div>
    </Section>
  );
}

interface RoomConsoleProps {
  roomCodeLabel: string;
  liveLabel: string;
  syncedLabel: string;
  listenersLabel: string;
  commentSample: string;
  hostLabel: string;
}

function RoomConsole({
  roomCodeLabel,
  liveLabel,
  syncedLabel,
  listenersLabel,
  commentSample,
  hostLabel,
}: RoomConsoleProps) {
  const prefersReducedMotion = useReducedMotion();

  return (
    <figure className="neu-raised overflow-hidden rounded-3xl border-none bg-[#e0e5ec] dark:bg-[#1e222b]">
      <figcaption className="flex items-center justify-between gap-4 border-b border-slate-300/40 dark:border-slate-700/40 px-5 py-4">
        <span className="flex items-baseline gap-2.5">
          <span className="font-mono text-xs font-bold uppercase tracking-[0.2em] text-slate-500 dark:text-slate-400">
            {roomCodeLabel}
          </span>
          <span className="font-mono text-sm font-bold tracking-[0.1em] text-slate-900 dark:text-slate-100">{ROOM_CODE}</span>
        </span>
        <span className="inline-flex items-center gap-2 font-mono text-xs font-bold uppercase tracking-[0.16em] text-red-500 dark:text-red-400">
          <motion.span
            className="size-2 rounded-full bg-red-500 dark:bg-red-400"
            animate={prefersReducedMotion ? undefined : { opacity: [1, 0.25, 1] }}
            transition={{ duration: 1.6, repeat: Infinity, ease: "easeInOut" }}
            aria-hidden="true"
          />
          {liveLabel}
        </span>
      </figcaption>

      <div className="flex items-center gap-2 border-b border-slate-300/40 dark:border-slate-700/40 px-5 py-4" aria-hidden="true">
        {PARTICIPANTS.map(({ initials, muted }, index) => (
          <span
            key={initials}
            className="neu-raised-sm relative flex size-10 items-center justify-center rounded-full bg-[#e0e5ec] dark:bg-[#1e222b] font-mono text-xs font-bold text-slate-800 dark:text-slate-200"
          >
            {initials}
            <span className="neu-pressed-sm absolute -bottom-1 -right-1 flex size-4 items-center justify-center rounded-full bg-[#e0e5ec] dark:bg-[#1e222b]">
              {muted ? (
                <MicOff className="size-2.5 text-slate-400" />
              ) : (
                <Mic className="size-2.5 text-indigo-600 dark:text-indigo-400" />
              )}
            </span>
            {index === 0 && (
              <span className="neu-pressed-sm absolute -top-2.5 left-1/2 -translate-x-1/2 whitespace-nowrap rounded-full bg-[#e0e5ec] px-1.5 dark:bg-[#1e222b] font-mono text-xs font-bold uppercase tracking-[0.08em] text-indigo-600 dark:text-indigo-400">
                {hostLabel}
              </span>
            )}
          </span>
        ))}
        <span className="ml-auto font-mono text-xs font-semibold text-slate-500 dark:text-slate-400">{listenersLabel}</span>
      </div>

      <div className="px-5 py-7" aria-hidden="true">
        <div className="flex items-center gap-4">
          <span className="neu-button-primary flex size-10 shrink-0 items-center justify-center rounded-full bg-indigo-600 text-white shadow-neu-raised-sm">
            <Play className="size-4 fill-current ml-0.5" />
          </span>

          <div className="relative h-10 flex-1">
            <motion.span
              className="neu-raised-sm absolute -top-1 z-10 max-w-[90%] -translate-x-1/2 truncate rounded-xl bg-[#e0e5ec] px-3 py-1.5 font-mono text-xs font-semibold text-slate-800 dark:bg-[#1e222b] dark:text-slate-200"
              style={{ left: `${COMMENT_POSITION * 100}%` }}
              initial={{ opacity: 0, y: 6 }}
              animate={
                prefersReducedMotion ? { opacity: 1, y: 0 } : { opacity: [0, 0, 1, 1, 0], y: [6, 6, 0, 0, 6] }
              }
              transition={{
                duration: LOOP_SECONDS,
                times: [0, COMMENT_TIME - 0.04, COMMENT_TIME, 0.96, 1],
                repeat: prefersReducedMotion ? 0 : Infinity,
                ease: "easeOut",
              }}
            >
              {commentSample}
            </motion.span>

            <span className="neu-pressed-sm absolute bottom-3 left-0 h-1.5 w-full rounded-full bg-[#e0e5ec] dark:bg-[#1e222b]" />
            <motion.span
              className="absolute bottom-3 left-0 h-1.5 rounded-full bg-indigo-600 dark:bg-indigo-400"
              initial={{ width: "0%" }}
              animate={
                prefersReducedMotion
                  ? { width: `${PLAYHEAD_END * 100}%` }
                  : { width: ["0%", `${PLAYHEAD_END * 100}%`] }
              }
              transition={{
                duration: LOOP_SECONDS,
                ease: "linear",
                repeat: prefersReducedMotion ? 0 : Infinity,
              }}
            />
            <span
              className="absolute bottom-2 size-2 -translate-x-1/2 rounded-full bg-indigo-600 dark:bg-indigo-400"
              style={{ left: `${COMMENT_POSITION * 100}%` }}
            />
          </div>

          <span className="shrink-0 font-mono text-xs font-semibold tabular-nums text-slate-600 dark:text-slate-400">
            03:58
          </span>
        </div>
      </div>

      <div className="neu-pressed-sm flex items-center gap-2.5 border-t border-slate-300/40 dark:border-slate-700/40 bg-[#e0e5ec] dark:bg-[#1e222b] px-5 py-3.5">
        <span className="waveform text-indigo-600 dark:text-indigo-400" aria-hidden="true" data-state={prefersReducedMotion ? "paused" : undefined}>
          <span className="bg-indigo-600 dark:bg-indigo-400" />
          <span className="bg-indigo-600 dark:bg-indigo-400" />
          <span className="bg-indigo-600 dark:bg-indigo-400" />
          <span className="bg-indigo-600 dark:bg-indigo-400" />
          <span className="bg-indigo-600 dark:bg-indigo-400" />
        </span>
        <span className="font-mono text-xs font-semibold uppercase tracking-[0.16em] text-slate-700 dark:text-slate-300">
          {syncedLabel}
        </span>
      </div>
    </figure>
  );
}
