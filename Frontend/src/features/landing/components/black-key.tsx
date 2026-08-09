"use client";

import { motion } from "framer-motion";
import { useCallback, useRef, useState } from "react";
import { createAudioPlayer, getBlackNoteFrequency, type Side } from "../lib/piano-audio";

const BLACK_KEY_HEIGHT = "var(--piano-black-height)";
const BLACK_KEY_MIN_WIDTH = "0.4rem";
const BLACK_KEY_MAX_WIDTH = "2rem";
const BLACK_KEY_WIDTH_RATIO = 0.6;

type BlackKeyProps = {
  side: Side;
  animationDelay: number;
  totalKeys: number;
  centerPercent: number;
};

export function BlackKey({ side, animationDelay, totalKeys, centerPercent }: BlackKeyProps) {
  const [isHovered, setIsHovered] = useState(false);
  const [isPressed, setIsPressed] = useState(false);
  const audioCtxRef = useRef<AudioContext | null>(null);

  const playNote = useCallback(() => {
    const frequency = getBlackNoteFrequency(side);
    createAudioPlayer(audioCtxRef, frequency, 0.25, 0.12);
  }, [side]);

  const activate = () => {
    setIsHovered(true);
    setIsPressed(true);
    playNote();
  };

  const deactivate = () => {
    setIsHovered(false);
    setIsPressed(false);
  };

  const handleMouseEnter = () => {
    if (typeof window === "undefined") return;
    if (window.matchMedia("(hover: hover)").matches) {
      activate();
    }
  };

  const handleMouseLeave = () => {
    if (typeof window === "undefined") return;
    if (window.matchMedia("(hover: hover)").matches) {
      deactivate();
    }
  };

  const handleTouchStart = (e: React.TouchEvent) => {
    e.preventDefault();
    activate();
  };

  const handleTouchEnd = (e: React.TouchEvent) => {
    e.preventDefault();
    deactivate();
  };

  return (
    <div
      className="pointer-events-auto absolute z-30"
      style={{
        width: `${(BLACK_KEY_WIDTH_RATIO / totalKeys) * 100}%`,
        minWidth: BLACK_KEY_MIN_WIDTH,
        maxWidth: BLACK_KEY_MAX_WIDTH,
        height: BLACK_KEY_HEIGHT,
        top: 0,
        left: `${centerPercent}%`,
        transform: "translateX(-50%)",
      }}
    >
      <motion.div
        className="h-full w-full cursor-pointer"
        style={{ transformOrigin: "top" }}
        initial={{ opacity: 0, y: -50 }}
        animate={{ opacity: 1, y: isPressed ? 3 : 0 }}
        transition={{ duration: 0.4, delay: animationDelay, ease: "easeOut" }}
        onMouseEnter={handleMouseEnter}
        onMouseLeave={handleMouseLeave}
        onTouchStart={handleTouchStart}
        onTouchEnd={handleTouchEnd}
      >
        <div
          className="absolute inset-0 overflow-hidden rounded-b-[2px]"
          style={{
            background: isHovered
              ? "linear-gradient(to bottom, #3b4557, #161d2b, #0a0f1a)"
              : "linear-gradient(to bottom, #252b38, #080c14, #020617)",
            boxShadow: isPressed
              ? "inset 0 3px 5px rgba(0,0,0,0.9), inset 0 -1px 1px rgba(255,255,255,0.05)"
              : "inset 0 -3px 4px rgba(255,255,255,0.1), 0 4px 8px rgba(0,0,0,0.4)",
          }}
        >
          <TopHighlight />
          <WoodGrain />
          <RightEdgeShadow />
          <LeftEdgeShadow />
        </div>
      </motion.div>
    </div>
  );
}

function TopHighlight() {
  return (
    <div className="absolute top-0 left-1/2 h-[2px] w-3/4 -translate-x-1/2 rounded-full bg-gradient-to-b from-white/30 to-transparent" />
  );
}

function WoodGrain() {
  return (
    <div
      className="pointer-events-none absolute inset-0 opacity-30"
      style={{
        backgroundImage:
          "repeating-linear-gradient(to right, transparent, transparent 1px, rgba(255,255,255,0.04) 1px, rgba(255,255,255,0.04) 2px)",
      }}
    />
  );
}

function RightEdgeShadow() {
  return <div className="absolute top-0 right-0 bottom-0 w-[1px] bg-slate-950/80" />;
}

function LeftEdgeShadow() {
  return <div className="absolute top-0 left-0 bottom-0 w-[1px] bg-slate-950/60" />;
}
