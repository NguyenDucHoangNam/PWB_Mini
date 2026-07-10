"use client";

import { useEffect, useState } from "react";
import { motion } from "framer-motion";
import { useTranslations } from "next-intl";
import { Keyboard } from "./keyboard";
import { FALL_DELAY, LEFT_WORD, RIGHT_WORD } from "../lib/piano-positions";

const KEYBOARD_GAP = "h-3 w-full sm:h-0 sm:w-2 md:w-16 lg:w-24";
const POINTER_EVENTS_DELAY = 2.5;
const HINT_DELAY = FALL_DELAY + 1.2;

export function LandingContent() {
  return (
    <section className="relative flex min-h-[calc(100vh-64px)] w-full flex-col items-center justify-center overflow-hidden bg-gradient-to-b from-white via-neutral-50 to-neutral-100 px-3 py-24 sm:px-4 sm:py-0 dark:from-black dark:via-neutral-950 dark:to-neutral-900">
      <BackgroundGlow />
      <PianoTitle />
      <HoverHint delay={HINT_DELAY} />
    </section>
  );
}

function SandParticles() {
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);

  if (!mounted) return null;

  const particles = Array.from({ length: 20 });
  return (
    <div className="absolute inset-0 pointer-events-none overflow-hidden z-0">
      {particles.map((_, i) => {
        const size = Math.random() * 3 + 1; // 1px to 4px
        const duration = Math.random() * 20 + 20; // 20s to 40s
        const delay = Math.random() * -20; // Start immediately
        const left = `${Math.random() * 100}%`;
        const startY = "110%";
        const endY = "-10%";

        return (
          <motion.div
            key={i}
            className="absolute rounded-full bg-neutral-400/10 dark:bg-white/10 blur-[0.5px]"
            style={{
              width: size,
              height: size,
              left,
            }}
            initial={{ y: startY, opacity: 0 }}
            animate={{
              y: [startY, endY],
              opacity: [0, 0.4, 0.4, 0],
              x: [0, Math.random() * 40 - 20, 0], // Gentle sway
            }}
            transition={{
              duration,
              repeat: Infinity,
              ease: "linear",
              delay,
            }}
          />
        );
      })}
    </div>
  );
}

function BackgroundGlow() {
  return (
    <div className="pointer-events-none absolute inset-0 overflow-hidden">
      {/* SVG Filter for Wavy Sea Displacement */}
      <svg className="absolute h-0 w-0" width="0" height="0">
        <defs>
          <filter id="sand-wave">
            <feTurbulence
              type="fractalNoise"
              baseFrequency="0.008 0.025"
              numOctaves="1"
              result="noise"
              seed="2"
            />
            <motion.feDisplacementMap
              in="SourceGraphic"
              in2="noise"
              xChannelSelector="R"
              yChannelSelector="G"
              animate={{
                scale: [15, 35, 20, 38, 15],
              }}
              transition={{
                duration: 16,
                ease: "easeInOut",
                repeat: Infinity,
              }}
            />
          </filter>
        </defs>
      </svg>

      {/* Soft spotlight behind the keyboard */}
      <div className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 h-[500px] w-[800px] rounded-full bg-neutral-200/30 blur-[120px] dark:bg-neutral-800/15" />

      {/* Moving Sand Image Background with Wave Filter */}
      <motion.div
        className="absolute inset-0 bg-cover bg-center opacity-[0.08] dark:opacity-[0.14] transition-opacity duration-300 mix-blend-luminosity scale-[1.15]"
        style={{
          backgroundImage: "url('/sand-bg.png')",
          filter: "url(#sand-wave)",
        }}
        animate={{
          x: [0, "1.5%", "-1.5%", "1%", 0],
          y: [0, "-1%", "1.5%", "-0.5%", 0],
        }}
        transition={{
          duration: 20,
          ease: "easeInOut",
          repeat: Infinity,
        }}
      />

      {/* Floating Sand Particles */}
      <SandParticles />
    </div>
  );
}

function PianoTitle() {
  return (
    <motion.div
      className="relative z-10 flex flex-col items-center gap-2 sm:flex-row sm:items-end sm:justify-center sm:gap-0"
      style={{ letterSpacing: "0.25rem" }}
      initial={{ pointerEvents: "none" }}
      animate={{ pointerEvents: "auto" }}
      transition={{ delay: POINTER_EVENTS_DELAY }}
    >
      <div className="w-full max-w-[420px] sm:max-w-none sm:w-auto">
        <Keyboard chars={LEFT_WORD} side="left" />
      </div>
      <div className={KEYBOARD_GAP} />
      <div className="w-full max-w-[470px] sm:max-w-none sm:w-auto">
        <Keyboard chars={RIGHT_WORD} side="right" />
      </div>
    </motion.div>
  );
}

function HoverHint({ delay }: { delay: number }) {
  const t = useTranslations("landing");
  return (
    <motion.p
      className="absolute bottom-12 mt-12 text-sm text-neutral-400 dark:text-neutral-500"
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay, duration: 0.6 }}
    >
      {t("hoverHint")}
    </motion.p>
  );
}
