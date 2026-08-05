"use client";

import { useEffect, useState, useSyncExternalStore } from "react";
import { motion } from "framer-motion";
import { useTranslations } from "next-intl";
import { Keyboard } from "./keyboard";
import { FALL_DELAY, LEFT_WORD, RIGHT_WORD } from "../lib/piano-positions";
import { generateSandParticles } from "../lib/generate-sand-particles";
import { SectionTagline } from "./section-tagline";
import { SectionFeatures } from "./section-features";
import { SectionDemo } from "./section-demo";
import { SectionHowItWorks } from "./section-how-it-works";
import { SectionStats } from "./section-stats";
import { SectionCta } from "./section-cta";

const KEYBOARD_GAP = "h-3 w-full sm:h-0 sm:w-2 md:w-16 lg:w-24";
const POINTER_EVENTS_DELAY = 2.5;
const HINT_DELAY = FALL_DELAY + 1.2;
const SAND_PARTICLE_COUNT = 20;

const subscribeMounted = (callback: () => void) => {
  if (typeof window === "undefined") return () => {};
  window.addEventListener("load", callback);
  return () => window.removeEventListener("load", callback);
};

const getMountedSnapshot = () => true;
const getServerMountedSnapshot = () => false;

export function LandingContent() {
  return (
    <>
      <section className="relative flex min-h-svh w-full flex-col items-center justify-center overflow-hidden bg-gradient-to-b from-white via-neutral-50 to-neutral-100 px-3 py-24 sm:px-4 sm:py-0 dark:from-black dark:via-neutral-950 dark:to-neutral-900">
        <BackgroundGlow />
        <PianoTitle />
        <HoverHint delay={HINT_DELAY} />
      </section>
      <SectionTagline />
      <SectionFeatures />
      <SectionDemo />
      <SectionHowItWorks />
      <SectionStats />
      <SectionCta />
    </>
  );
}

interface SandParticle {
  size: number;
  duration: number;
  delay: number;
  left: string;
  sway: number;
}

function SandParticles() {
  const mounted = useSyncExternalStore(subscribeMounted, getMountedSnapshot, getServerMountedSnapshot);
  const [particles, setParticles] = useState<SandParticle[]>([]);

  useEffect(() => {
    if (!mounted || particles.length > 0) return;
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setParticles(generateSandParticles(SAND_PARTICLE_COUNT));
  }, [mounted, particles.length]);

  if (!mounted || particles.length === 0) return null;

  return (
    <div className="absolute inset-0 pointer-events-none overflow-hidden z-0">
      {particles.map((p, i) => (
        <motion.div
          key={i}
          className="absolute rounded-full bg-neutral-400/10 dark:bg-white/10 blur-[0.5px]"
          style={{
            width: p.size,
            height: p.size,
            left: p.left,
          }}
          initial={{ y: "110%", opacity: 0 }}
          animate={{
            y: ["110%", "-10%"],
            opacity: [0, 0.4, 0.4, 0],
            x: [0, p.sway, 0],
          }}
          transition={{
            duration: p.duration,
            repeat: Infinity,
            ease: "linear",
            delay: p.delay,
          }}
        />
      ))}
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
