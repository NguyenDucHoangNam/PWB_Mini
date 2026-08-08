"use client";

import { AnimatePresence, motion } from "framer-motion";
import { useCallback, useRef, useState } from "react";
import { createAudioPlayer, getWhiteNoteFrequency, type Side } from "../lib/piano-audio";

const KEY_GLYPH_SIZE = "var(--piano-key-glyph)";
const KEY_BOX_SIZE = "var(--piano-key-box)";
const WHITE_KEY_HEIGHT = "var(--piano-white-height)";

type PianoKeyProps = {
  char: string;
  index: number;
  side: Side;
  animationDelay: number;
};

export function PianoKey({ char, index, side, animationDelay }: PianoKeyProps) {
  const [isHovered, setIsHovered] = useState(false);
  const [isPressed, setIsPressed] = useState(false);
  const audioCtxRef = useRef<AudioContext | null>(null);

  const playNote = useCallback(() => {
    const frequency = getWhiteNoteFrequency(index, side);
    createAudioPlayer(audioCtxRef, frequency, 0.3, 0.15);
  }, [index, side]);

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
    <div className="relative flex w-full flex-col items-center">
      <KeyGlyph
        char={char}
        side={side}
        isHovered={isHovered}
        isPressed={isPressed}
        animationDelay={animationDelay}
        onMouseEnter={handleMouseEnter}
        onMouseLeave={handleMouseLeave}
        onTouchStart={handleTouchStart}
        onTouchEnd={handleTouchEnd}
      />
      <WhiteKeyBody
        isHovered={isHovered}
        isPressed={isPressed}
        animationDelay={animationDelay}
        onMouseEnter={handleMouseEnter}
        onMouseLeave={handleMouseLeave}
        onTouchStart={handleTouchStart}
        onTouchEnd={handleTouchEnd}
      />
      <HoverGlow isHovered={isHovered} />
    </div>
  );
}

type KeyGlyphProps = {
  char: string;
  side: Side;
  isHovered: boolean;
  isPressed: boolean;
  animationDelay: number;
  onMouseEnter: () => void;
  onMouseLeave: () => void;
  onTouchStart: (e: React.TouchEvent) => void;
  onTouchEnd: (e: React.TouchEvent) => void;
};

function KeyGlyph({
  char,
  side,
  isHovered,
  isPressed,
  animationDelay,
  onMouseEnter,
  onMouseLeave,
  onTouchStart,
  onTouchEnd,
}: KeyGlyphProps) {
  return (
    <div
      className="relative flex items-center justify-center"
      style={{ width: KEY_BOX_SIZE, height: KEY_BOX_SIZE }}
    >
      <GhostChar char={char} side={side} animationDelay={animationDelay} />
      <RealChar
        char={char}
        isHovered={isHovered}
        isPressed={isPressed}
        animationDelay={animationDelay}
        onMouseEnter={onMouseEnter}
        onMouseLeave={onMouseLeave}
        onTouchStart={onTouchStart}
        onTouchEnd={onTouchEnd}
      />
    </div>
  );
}

function GhostChar({
  char,
  side,
  animationDelay,
}: {
  char: string;
  side: Side;
  animationDelay: number;
}) {
  return (
    <motion.div
      className="pointer-events-none absolute inset-0 z-0 flex items-center justify-center"
      initial={{ opacity: 1 }}
      animate={{ opacity: 0 }}
      transition={{ delay: animationDelay + 0.2, duration: 0.2 }}
    >
      <motion.span
        className="inline-block font-black tracking-tight uppercase text-transparent"
        style={{
          fontSize: KEY_GLYPH_SIZE,
          WebkitTextStroke: "1.5px var(--ghost-stroke)",
          textShadow: "0 0 20px rgba(0, 0, 0, 0.3)",
        }}
        initial={{ x: side === "left" ? "-50vw" : "50vw", opacity: 0 }}
        animate={{ x: 0, opacity: 1 }}
        transition={{ duration: 0.8, ease: "easeOut" }}
      >
        {char}
      </motion.span>
    </motion.div>
  );
}

type RealCharProps = {
  char: string;
  isHovered: boolean;
  isPressed: boolean;
  animationDelay: number;
  onMouseEnter: () => void;
  onMouseLeave: () => void;
  onTouchStart: (e: React.TouchEvent) => void;
  onTouchEnd: (e: React.TouchEvent) => void;
};

function RealChar({
  char,
  isHovered,
  isPressed,
  animationDelay,
  onMouseEnter,
  onMouseLeave,
  onTouchStart,
  onTouchEnd,
}: RealCharProps) {
  const colorClass = isHovered
    ? "text-white dark:text-neutral-900"
    : "text-neutral-900 dark:text-white";

  return (
    <motion.span
      className={`relative z-10 inline-block cursor-pointer font-black tracking-tight select-none uppercase transition-colors duration-200 ${colorClass}`}
      style={{
        fontSize: KEY_GLYPH_SIZE,
        textShadow: isHovered ? "0px 4px 6px rgba(0,0,0,0.3)" : "0px 4px 6px rgba(0,0,0,0.1)",
      }}
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
      onTouchStart={onTouchStart}
      onTouchEnd={onTouchEnd}
      initial={{ opacity: 0, y: -200, scale: 1.2 }}
      animate={{
        opacity: 1,
        y: isPressed ? 8 : 0,
        scale: isPressed ? 0.95 : 1,
      }}
      transition={{ duration: 0.6, delay: animationDelay, type: "spring", bounce: 0.4 }}
    >
      {char}
    </motion.span>
  );
}

type WhiteKeyBodyProps = {
  isHovered: boolean;
  isPressed: boolean;
  animationDelay: number;
  onMouseEnter: () => void;
  onMouseLeave: () => void;
  onTouchStart: (e: React.TouchEvent) => void;
  onTouchEnd: (e: React.TouchEvent) => void;
};

function WhiteKeyBody({
  isHovered,
  isPressed,
  animationDelay,
  onMouseEnter,
  onMouseLeave,
  onTouchStart,
  onTouchEnd,
}: WhiteKeyBodyProps) {
  const pressedShadow = "inset 0 3px 6px rgba(0,0,0,0.2)";
  const idleShadow = "inset 0 -3px 6px rgba(0,0,0,0.06)";

  return (
    <motion.div
      className="relative flex w-full flex-col"
      style={{ height: WHITE_KEY_HEIGHT, transformOrigin: "top" }}
      initial={{ opacity: 0, y: 15, scale: 0.8 }}
      animate={{ opacity: 1, scale: 1, y: isPressed ? 4 : 0 }}
      transition={{ duration: 0.4, delay: animationDelay + 0.1, ease: "easeOut" }}
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
      onTouchStart={onTouchStart}
      onTouchEnd={onTouchEnd}
    >
      <div
        className="absolute inset-0 overflow-hidden rounded-b-[2px]"
        style={{ boxShadow: isPressed ? pressedShadow : idleShadow }}
      >
        <KeyFace isHovered={isHovered} />
        <WoodGrain />
        <TopHighlight />
        <RightEdgeShadow />
        <LeftEdgeShadow />
        <CenterMark />
      </div>
    </motion.div>
  );
}

function KeyFace({ isHovered }: { isHovered: boolean }) {
  const gradientClass = isHovered
    ? "bg-gradient-to-b from-neutral-200 via-neutral-100 to-neutral-200 dark:from-neutral-300 dark:via-neutral-200 dark:to-neutral-300"
    : "bg-gradient-to-b from-white via-neutral-50 to-neutral-100 dark:from-neutral-100 dark:via-neutral-50 dark:to-neutral-200";

  return <div className={`absolute inset-0 ${gradientClass}`} />;
}

function WoodGrain() {
  return (
    <div
      className="pointer-events-none absolute inset-0 opacity-40 dark:opacity-20"
      style={{
        backgroundImage:
          "repeating-linear-gradient(to right, transparent, transparent 1px, rgba(0,0,0,0.03) 1px, rgba(0,0,0,0.03) 2px)",
      }}
    />
  );
}

function TopHighlight() {
  return (
    <div className="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-b from-white/90 to-transparent dark:from-white/30" />
  );
}

function RightEdgeShadow() {
  return (
    <div className="absolute top-0 right-0 bottom-0 w-[1px] bg-gradient-to-b from-neutral-300/40 via-neutral-400/60 to-neutral-500/80 dark:from-neutral-600/40 dark:via-neutral-700/60 dark:to-neutral-800/80" />
  );
}

function LeftEdgeShadow() {
  return (
    <div className="absolute top-0 left-0 bottom-0 w-[1px] bg-gradient-to-b from-neutral-200/40 via-neutral-300/30 to-neutral-400/40 dark:from-neutral-600/30 dark:via-neutral-700/30 dark:to-neutral-800/40" />
  );
}

function CenterMark() {
  return (
    <div className="absolute bottom-3 left-1/2 h-1 w-1 -translate-x-1/2 rounded-full bg-neutral-300/70 dark:bg-neutral-500/50" />
  );
}

function HoverGlow({ isHovered }: { isHovered: boolean }) {
  return (
    <AnimatePresence>
      {isHovered && (
        <motion.div
          className="absolute -z-10 rounded-full bg-neutral-500/20 blur-xl dark:bg-neutral-300/20"
          initial={{ opacity: 0, scale: 0.5 }}
          animate={{ opacity: 1, scale: 1.2 }}
          exit={{ opacity: 0, scale: 0.5 }}
          style={{ top: "30%", width: "120%", height: "150%" }}
        />
      )}
    </AnimatePresence>
  );
}
