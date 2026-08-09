"use client";

import { motion, useReducedMotion } from "framer-motion";
import { useTranslations } from "next-intl";
import { Mic, MicOff, Play } from "lucide-react";
import {
  Groove,
  NumberedPoint,
  Reveal,
  RevealGroup,
  Section,
  SectionHeader,
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
    <Section tone="trough">
      <SectionHeader
        eyebrow={t("eyebrow")}
        title={t("title")}
        lead={t("lead")}
        align="center"
      />

      {/* The console leads at the section's full width instead of sharing a column with
          the copy — the previous mirrored two-column layout read as a repeat of the
          section above, and a narrower card would break the page's left/right edges. */}
      <Reveal className="mt-14 w-full lg:mt-16">
        <RoomConsole
          roomCodeLabel={t("roomCodeLabel")}
          liveLabel={t("liveLabel")}
          syncedLabel={t("syncedLabel")}
          listenersLabel={t("listenersLabel")}
          commentSample={t("commentSample")}
          hostLabel={t("hostLabel")}
        />
      </Reveal>

      <RevealGroup className="mt-16 grid gap-x-10 gap-y-8 md:grid-cols-3">
        {POINT_KEYS.map((key, index) => (
          <NumberedPoint
            key={key}
            index={index + 1}
            title={t(`${key}Title`)}
            body={t(`${key}Body`)}
          />
        ))}
      </RevealGroup>
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
    <figure className="neu-raised-lg rounded-3xl border-none p-6 sm:p-8">
      <figcaption className="flex items-center justify-between gap-4">
        <span className="flex items-baseline gap-2.5">
          <span className="font-mono text-xs font-bold uppercase tracking-[0.2em] text-slate-500 dark:text-slate-400">
            {roomCodeLabel}
          </span>
          <span className="font-mono text-sm font-bold tracking-[0.1em] text-slate-900 dark:text-slate-100">
            {ROOM_CODE}
          </span>
        </span>
        <span className="neu-pressed-sm inline-flex items-center gap-2 rounded-full px-3.5 py-1.5 font-mono text-xs font-bold uppercase tracking-[0.16em] text-red-500 dark:text-red-400">
          <motion.span
            className="size-2 rounded-full bg-red-500 dark:bg-red-400"
            animate={prefersReducedMotion ? undefined : { opacity: [1, 0.25, 1] }}
            transition={{ duration: 1.6, repeat: Infinity, ease: "easeInOut" }}
            aria-hidden="true"
          />
          {liveLabel}
        </span>
      </figcaption>

      <Groove className="my-6" />

      <div className="flex items-center gap-3.5" aria-hidden="true">
        {PARTICIPANTS.map(({ initials, muted }, index) => (
          <span
            key={initials}
            className="neu-raised-sm relative flex size-11 items-center justify-center rounded-full font-mono text-xs font-bold text-slate-800 dark:text-slate-200"
          >
            {initials}
            <span className="neu-pressed-sm absolute -bottom-1 -right-1 flex size-4.5 items-center justify-center rounded-full">
              {muted ? (
                <MicOff className="size-2.5 text-slate-400" />
              ) : (
                <Mic className="size-2.5 text-indigo-600 dark:text-indigo-400" />
              )}
            </span>
            {index === 0 && (
              <span className="neu-pressed-sm absolute -top-2.5 left-1/2 -translate-x-1/2 whitespace-nowrap rounded-full px-2 py-0.5 font-mono text-[0.625rem] font-bold uppercase tracking-[0.08em] text-indigo-600 dark:text-indigo-400">
                {hostLabel}
              </span>
            )}
          </span>
        ))}
        <span className="ml-auto font-mono text-xs font-semibold text-slate-500 dark:text-slate-400">
          {listenersLabel}
        </span>
      </div>

      {/* Transport sits in a well: the console is the device, this is the slot cut
          into it. */}
      <div className="neu-pressed mt-7 rounded-2xl px-5 py-6" aria-hidden="true">
        <div className="flex items-center gap-4">
          <span className="flex size-11 shrink-0 items-center justify-center rounded-full bg-indigo-600 text-white shadow-neu-raised-sm dark:bg-indigo-500">
            <Play className="ml-0.5 size-4 fill-current" />
          </span>

          <div className="relative h-10 flex-1">
            <motion.span
              className="neu-raised-sm absolute -top-1 z-10 max-w-[90%] -translate-x-1/2 truncate rounded-xl px-3 py-1.5 font-mono text-xs font-semibold text-slate-800 dark:text-slate-200"
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

            <span className="neu-pressed-sm absolute bottom-3 left-0 h-2 w-full rounded-full" />
            <motion.span
              className="absolute bottom-3 left-0 h-2 rounded-full bg-indigo-600 dark:bg-indigo-400"
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
              className="absolute bottom-2 size-4 -translate-x-1/2 rounded-full bg-indigo-600 shadow-neu-raised-sm dark:bg-indigo-400"
              style={{ left: `${COMMENT_POSITION * 100}%` }}
            />
          </div>

          <span className="shrink-0 font-mono text-xs font-semibold tabular-nums text-slate-600 dark:text-slate-400">
            03:58
          </span>
        </div>
      </div>

      <div className="mt-6 flex items-center gap-2.5">
        <span
          className="waveform text-indigo-600 dark:text-indigo-400"
          aria-hidden="true"
          data-state={prefersReducedMotion ? "paused" : undefined}
        >
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
