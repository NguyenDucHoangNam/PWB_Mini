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
} from "./section-primitives";

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
    <figure className="overflow-hidden rounded-2xl border border-border bg-card">
      <figcaption className="flex items-center justify-between gap-4 border-b border-border px-5 py-4">
        <span className="flex items-baseline gap-2.5">
          <span className="font-mono text-xs uppercase tracking-[0.2em] text-muted-foreground">
            {roomCodeLabel}
          </span>
          <span className="font-mono text-sm tracking-[0.1em] text-foreground">{ROOM_CODE}</span>
        </span>
        <span className="inline-flex items-center gap-2 font-mono text-xs uppercase tracking-[0.16em] text-foreground">
          <motion.span
            className="size-2 rounded-full bg-foreground"
            animate={prefersReducedMotion ? undefined : { opacity: [1, 0.25, 1] }}
            transition={{ duration: 1.6, repeat: Infinity, ease: "easeInOut" }}
            aria-hidden="true"
          />
          {liveLabel}
        </span>
      </figcaption>

      <div className="flex items-center gap-2 border-b border-border px-5 py-4" aria-hidden="true">
        {PARTICIPANTS.map(({ initials, muted }, index) => (
          <span
            key={initials}
            className="relative flex size-10 items-center justify-center rounded-full border border-border bg-background font-mono text-xs text-foreground"
          >
            {initials}
            <span className="absolute -bottom-1 -right-1 flex size-4 items-center justify-center rounded-full border border-border bg-card">
              {muted ? (
                <MicOff className="size-2.5 text-muted-foreground" />
              ) : (
                <Mic className="size-2.5 text-foreground" />
              )}
            </span>
            {index === 0 && (
              <span className="absolute -top-2.5 left-1/2 -translate-x-1/2 whitespace-nowrap rounded-full border border-border bg-card px-1.5 font-mono text-xs uppercase tracking-[0.08em] text-muted-foreground">
                {hostLabel}
              </span>
            )}
          </span>
        ))}
        <span className="ml-auto font-mono text-xs text-muted-foreground">{listenersLabel}</span>
      </div>

      <div className="px-5 py-7" aria-hidden="true">
        <div className="flex items-center gap-4">
          <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-primary text-primary-foreground">
            <Play className="size-4 fill-current" />
          </span>

          <div className="relative h-10 flex-1">
            {/* Comment pinned to a position on the timeline. */}
            <motion.span
              className="absolute -top-1 z-10 max-w-[90%] -translate-x-1/2 truncate rounded-lg border border-border bg-popover px-2.5 py-1.5 text-xs text-popover-foreground shadow-md"
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

            <span className="absolute bottom-3 left-0 h-1 w-full rounded-full bg-muted" />
            <motion.span
              className="absolute bottom-3 left-0 h-1 rounded-full bg-foreground"
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
              className="absolute bottom-1.5 size-1.5 -translate-x-1/2 rounded-full border border-border bg-foreground"
              style={{ left: `${COMMENT_POSITION * 100}%` }}
            />
          </div>

          <span className="shrink-0 font-mono text-xs tabular-nums text-muted-foreground">
            03:58
          </span>
        </div>
      </div>

      <div className="flex items-center gap-2.5 border-t border-border bg-background px-5 py-3.5">
        <span className="waveform text-foreground" aria-hidden="true" data-state={prefersReducedMotion ? "paused" : undefined}>
          <span />
          <span />
          <span />
          <span />
          <span />
        </span>
        <span className="font-mono text-xs uppercase tracking-[0.16em] text-muted-foreground">
          {syncedLabel}
        </span>
      </div>
    </figure>
  );
}
