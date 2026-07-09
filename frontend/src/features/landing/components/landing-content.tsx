"use client";

import { motion } from "framer-motion";
import { Keyboard } from "./keyboard";
import { FALL_DELAY, LEFT_WORD, RIGHT_WORD } from "../lib/piano-positions";

const KEYBOARD_GAP = "w-8 sm:w-16 md:w-24";
const POINTER_EVENTS_DELAY = 2.5;
const HINT_DELAY = FALL_DELAY + 1.2;

export function LandingContent() {
  return (
    <section className="relative flex min-h-[calc(100vh-64px)] w-full flex-col items-center justify-center overflow-hidden bg-gradient-to-b from-white via-neutral-50 to-neutral-100 px-4 dark:from-black dark:via-neutral-950 dark:to-neutral-900">
      <BackgroundGlow />
      <PianoTitle />
      <HoverHint delay={HINT_DELAY} />
    </section>
  );
}

function BackgroundGlow() {
  return (
    <div className="pointer-events-none absolute inset-0 overflow-hidden">
      <div className="absolute -left-32 -top-32 h-80 w-80 rounded-full bg-neutral-500/5 blur-3xl dark:bg-neutral-500/10" />
      <div className="absolute -right-32 -bottom-32 h-80 w-80 rounded-full bg-neutral-500/5 blur-3xl dark:bg-neutral-500/10" />
    </div>
  );
}

function PianoTitle() {
  return (
    <motion.div
      className="relative z-10 flex items-end justify-center"
      style={{ letterSpacing: "0.25rem" }}
      initial={{ pointerEvents: "none" }}
      animate={{ pointerEvents: "auto" }}
      transition={{ delay: POINTER_EVENTS_DELAY }}
    >
      <Keyboard chars={LEFT_WORD} side="left" />
      <div className={KEYBOARD_GAP} />
      <Keyboard chars={RIGHT_WORD} side="right" />
    </motion.div>
  );
}

function HoverHint({ delay }: { delay: number }) {
  return (
    <motion.p
      className="absolute bottom-12 mt-12 text-sm text-neutral-400 dark:text-neutral-500"
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay, duration: 0.6 }}
    >
      Hover to play notes
    </motion.p>
  );
}
