"use client";

import { useState, useRef, useCallback } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { LandingRedirectGuard } from "@/features/auth/components/landing-redirect-guard";

// Piano note frequencies (C4 to B4)
const NOTES = [261.63, 293.66, 329.63, 349.23, 392.0, 440.0, 493.88, 523.25];

function PianoKey({
  char,
  index,
  side,
  animationDelay,
}: {
  char: string;
  index: number;
  side: "left" | "right";
  animationDelay: number;
}) {
  const [isHovered, setIsHovered] = useState(false);
  const [isPressed, setIsPressed] = useState(false);
  const audioCtxRef = useRef<AudioContext | null>(null);

  const playNote = useCallback(() => {
    if (!audioCtxRef.current) {
      audioCtxRef.current = new AudioContext();
    }
    const audioCtx = audioCtxRef.current;

    const oscillator = audioCtx.createOscillator();
    const gainNode = audioCtx.createGain();

    const noteIndex = index % NOTES.length;
    const baseFreq = NOTES[noteIndex];
    const freq = side === "left" ? baseFreq : baseFreq * 1.05;

    oscillator.frequency.setValueAtTime(freq, audioCtx.currentTime);
    oscillator.type = "triangle";

    gainNode.gain.setValueAtTime(0, audioCtx.currentTime);
    gainNode.gain.linearRampToValueAtTime(0.3, audioCtx.currentTime + 0.01);
    gainNode.gain.exponentialRampToValueAtTime(0.15, audioCtx.currentTime + 0.1);
    gainNode.gain.exponentialRampToValueAtTime(0.01, audioCtx.currentTime + 0.5);

    oscillator.connect(gainNode);
    gainNode.connect(audioCtx.destination);

    oscillator.start();
    oscillator.stop(audioCtx.currentTime + 0.5);
  }, [index, side]);

  const handleMouseEnter = () => {
    setIsHovered(true);
    setIsPressed(true);
    playNote();
  };

  const handleMouseLeave = () => {
    setIsHovered(false);
    setIsPressed(false);
  };

  return (
    <div className="relative flex w-full flex-col items-center">

      {/* VÙNG CHỨA KÝ TỰ */}
      <div
        className="relative flex items-center justify-center"
        style={{ width: "clamp(2.2rem, 5vw, 3.5rem)", height: "clamp(2.2rem, 5vw, 3.5rem)" }}
      >

        {/* Phase 1: Ghost Text */}
        <motion.div
          className="absolute inset-0 z-0 flex items-center justify-center pointer-events-none"
          initial={{ opacity: 1 }}
          animate={{ opacity: 0 }}
          transition={{ delay: animationDelay + 0.2, duration: 0.2 }}
        >
          <motion.span
            className="inline-block font-black tracking-tight text-transparent uppercase"
            style={{
              fontSize: "clamp(1.5rem, 5vw, 3.5rem)",
              WebkitTextStroke: "1.5px rgba(0, 0, 0, 0.6)",
              textShadow: "0 0 20px rgba(0, 0, 0, 0.3)",
            }}
            initial={{ x: side === "left" ? "-50vw" : "50vw", opacity: 0 }}
            animate={{ x: 0, opacity: 1 }}
            transition={{ duration: 0.8, ease: "easeOut" }}
          >
            {char}
          </motion.span>
        </motion.div>

        {/* Phase 2: Chữ thật thả rơi */}
        <motion.span
          className={`relative z-10 inline-block cursor-pointer font-black tracking-tight select-none uppercase transition-colors duration-200 ${
            isHovered
              ? "text-neutral-50 dark:text-neutral-900"
              : "text-neutral-900 dark:text-neutral-50"
          }`}
          style={{
            fontSize: "clamp(1.5rem, 5vw, 3.5rem)",
            textShadow: isHovered
              ? "0px 4px 6px rgba(0,0,0,0.3)"
              : "0px 4px 6px rgba(0,0,0,0.1)",
          }}
          onMouseEnter={handleMouseEnter}
          onMouseLeave={handleMouseLeave}
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
      </div>

      {/* PHÍM PIANO CHÂN THỰC - WHITE KEY */}
      <motion.div
        className="relative flex w-full flex-col"
        style={{
          height: "clamp(5rem, 12vw, 8rem)",
          transformOrigin: "top",
        }}
        initial={{ opacity: 0, y: 15, scale: 0.8 }}
        animate={{
          opacity: 1,
          scale: 1,
          y: isPressed ? 4 : 0,
        }}
        transition={{
          duration: 0.4,
          delay: animationDelay + 0.1,
          ease: "easeOut",
        }}
        onMouseEnter={handleMouseEnter}
        onMouseLeave={handleMouseLeave}
      >
        {/* Lớp mặt phím - Ivory */}
        <div
          className="absolute inset-0 overflow-hidden rounded-b-[2px]"
          style={{
            boxShadow: isPressed
              ? "inset 0 3px 6px rgba(0,0,0,0.2)"
              : "inset 0 -3px 6px rgba(0,0,0,0.06)",
          }}
        >
          {/* Mặt phím trắng với gradient */}
          <div
            className={`absolute inset-0 ${
              isHovered
                ? "bg-gradient-to-b from-neutral-200 via-neutral-100 to-neutral-200 dark:from-neutral-300 dark:via-neutral-200 dark:to-neutral-300"
                : "bg-gradient-to-b from-white via-neutral-50 to-neutral-100 dark:from-neutral-100 dark:via-neutral-50 dark:to-neutral-200"
            }`}
          />

          {/* Van ngang mờ - vân gỗ nhẹ */}
          <div
            className="absolute inset-0 opacity-40 dark:opacity-20 pointer-events-none"
            style={{
              backgroundImage:
                "repeating-linear-gradient(to right, transparent, transparent 1px, rgba(0,0,0,0.03) 1px, rgba(0,0,0,0.03) 2px)",
            }}
          />

          {/* Highlight viền trên */}
          <div className="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-b from-white/90 to-transparent dark:from-white/30" />

          {/* Bóng đổ cạnh phải (khe giữa 2 phím trắng) */}
          <div className="absolute top-0 right-0 bottom-0 w-[1px] bg-gradient-to-b from-neutral-300/40 via-neutral-400/60 to-neutral-500/80 dark:from-neutral-600/40 dark:via-neutral-700/60 dark:to-neutral-800/80" />

          {/* Bóng đổ cạnh trái */}
          <div className="absolute top-0 left-0 bottom-0 w-[1px] bg-gradient-to-b from-neutral-200/40 via-neutral-300/30 to-neutral-400/40 dark:from-neutral-600/30 dark:via-neutral-700/30 dark:to-neutral-800/40" />

          {/* Vet ô nhỏ ở giữa phím */}
          <div className="absolute bottom-3 left-1/2 h-1 w-1 -translate-x-1/2 rounded-full bg-neutral-300/70 dark:bg-neutral-500/50" />
        </div>
      </motion.div>

      {/* Glow on hover */}
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
    </div>
  );
}

/**
 * BLACK KEY - phím đen nằm chồng lên giữa 2 phím trắng
 */
function BlackKey({
  side,
  animationDelay,
  totalKeys,
  centerPercent,
  hoverColor = false,
}: {
  side: "left" | "right";
  animationDelay: number;
  totalKeys: number;
  centerPercent: number;
  hoverColor?: boolean;
}) {
  const [isHovered, setIsHovered] = useState(false);
  const [isPressed, setIsPressed] = useState(false);
  const audioCtxRef = useRef<AudioContext | null>(null);

  const playNote = useCallback(() => {
    if (!audioCtxRef.current) {
      audioCtxRef.current = new AudioContext();
    }
    const audioCtx = audioCtxRef.current;
    const oscillator = audioCtx.createOscillator();
    const gainNode = audioCtx.createGain();

    // Tần số phím đen (sharp): C# = 277.18, D# = 311.13, F# = 369.99, G# = 415.30, A# = 466.16
    const blackNotes = [277.18, 311.13, 369.99, 415.30, 466.16];
    const noteIndex = Math.floor(Math.random() * blackNotes.length);
    const freq = side === "left" ? blackNotes[noteIndex] : blackNotes[noteIndex] * 1.05;

    oscillator.frequency.setValueAtTime(freq, audioCtx.currentTime);
    oscillator.type = "triangle";

    gainNode.gain.setValueAtTime(0, audioCtx.currentTime);
    gainNode.gain.linearRampToValueAtTime(0.25, audioCtx.currentTime + 0.01);
    gainNode.gain.exponentialRampToValueAtTime(0.12, audioCtx.currentTime + 0.1);
    gainNode.gain.exponentialRampToValueAtTime(0.01, audioCtx.currentTime + 0.5);

    oscillator.connect(gainNode);
    gainNode.connect(audioCtx.destination);

    oscillator.start();
    oscillator.stop(audioCtx.currentTime + 0.5);
  }, [side]);

  const handleMouseEnter = () => {
    setIsHovered(true);
    setIsPressed(true);
    playNote();
  };

  const handleMouseLeave = () => {
    setIsHovered(false);
    setIsPressed(false);
  };

  return (
    // Wrapper div: absolute position theo %, căn giữa bằng translateX(-50%)
    // Không dùng motion ở đây để tránh conflict với translate
    <div
      className="pointer-events-auto absolute z-30"
      style={{
        // Phím đen rộng bằng 60% phím trắng - chuẩn piano.
        // Vì phím trắng fill đều 100% width container qua grid 1fr,
        // nên 1 phím trắng = 100% / totalKeys → phím đen = 60% / totalKeys.
        width: `${(0.6 / totalKeys) * 100}%`,
        minWidth: "0.6rem",
        maxWidth: "2rem",
        height: "clamp(3rem, 7vw, 4.8rem)",
        top: 0,
        // Đặt phím đen tại vị trí CHÍNH GIỮA khoảng giữa 2 phím trắng.
        // centerPercent = (i + 0.5) / totalKeys * 100.
        left: `${centerPercent}%`,
        transform: "translateX(-50%)",
      }}
    >
      <motion.div
        className="h-full w-full cursor-pointer"
        style={{ transformOrigin: "top" }}
        initial={{ opacity: 0, y: -50 }}
        animate={{
          opacity: 1,
          y: isPressed ? 3 : 0,
        }}
        transition={{
          duration: 0.4,
          delay: animationDelay,
          ease: "easeOut",
        }}
        onMouseEnter={handleMouseEnter}
        onMouseLeave={handleMouseLeave}
      >
        {/* Mặt phím đen - Ebony */}
      <div
        className="absolute inset-0 overflow-hidden rounded-b-[2px]"
        style={{
          background: isHovered
            ? "linear-gradient(to bottom, #404040, #1a1a1a, #0d0d0d)"
            : "linear-gradient(to bottom, #2a2a2a, #0a0a0a, #000000)",
          boxShadow: isPressed
            ? "inset 0 3px 5px rgba(0,0,0,0.9), inset 0 -1px 1px rgba(255,255,255,0.05)"
            : "inset 0 -3px 4px rgba(255,255,255,0.1), 0 4px 8px rgba(0,0,0,0.4)",
        }}
      >
        {/* Highlight trên đỉnh phím đen (ánh sáng phản chiếu) */}
        <div className="absolute top-0 left-1/2 h-[2px] w-3/4 -translate-x-1/2 rounded-full bg-gradient-to-b from-white/30 to-transparent" />

        {/* Van gỗ dọc */}
        <div
          className="absolute inset-0 opacity-30 pointer-events-none"
          style={{
            backgroundImage:
              "repeating-linear-gradient(to right, transparent, transparent 1px, rgba(255,255,255,0.04) 1px, rgba(255,255,255,0.04) 2px)",
          }}
        />

        {/* Bóng đổ cạnh phải */}
        <div className="absolute top-0 right-0 bottom-0 w-[1px] bg-black/80" />

        {/* Bóng đổ cạnh trái */}
        <div className="absolute top-0 left-0 bottom-0 w-[1px] bg-black/60" />
      </div>
      </motion.div>
    </div>
  );
}

const leftWord = "PRODUCER".split("");
const rightWord = "WORKBENCH".split("");
const rightWordCount = rightWord.length;
const FALL_DELAY = 1.0;

/**
 * Mapping các vị trí phím đen trong 1 quãng tám:
 * - White keys: C(0) D(1) E(2) F(3) G(4) A(5) B(6)
 * - Black keys nằm ở: giữa C-D, D-E, F-G, G-A, A-B (KHÔNG có giữa E-F và B-C)
 *
 * Trong hệ 12 semitones: C(0) C#(0.5) D(1) D#(1.5) E(2) F(3) F#(3.5) G(4) G#(4.5) A(5) A#(5.5) B(6)
 *
 * BLACK_KEY_OCTAVE_INDICES chứa index (trong 1 quãng tám) của các phím trắng
 * CÓ phím đen BÊN PHẢI: C(0), D(1), F(3), G(4), A(5). Thiếu E(2) và B(6) vì
 * giữa E-F và B-C không có phím đen (đúng chuẩn piano).
 */
const BLACK_KEY_OCTAVE_INDICES = [0, 1, 3, 4, 5];

/**
 * Tính các vị trí đặt phím đen cho 1 nhóm chữ cái.
 *
 * Công thức toán học chính xác (chuẩn piano):
 *   - Phím trắng thứ `i` chiếm từ `i / N` đến `(i+1) / N` của container.
 *   - Biên giữa phím trắng `i` và phím trắng `i+1` nằm tại `(i+1) / N * 100%`.
 *   - Phím đen nằm CHỒNG LÊN biên giữa 2 phím trắng (nửa nằm trong phím i,
 *     nửa nằm trong phím i+1) → tâm = `(i + 1) / N`.
 *   - Với `translateX(-50%)` phím đen được căn giữa tại điểm đó.
 *
 * Ví dụ: phím đen C# nằm giữa C(0) và D(1) với 8 phím:
 *   center = (0 + 1) / 8 * 100 = 12.5% (đường biên giữa P và R).
 *
 * Điều kiện render:
 *   1. Phím trắng `i` có phím đen bên phải (i % 7 ∈ [0,1,3,4,5]).
 *   2. Phím trắng `i+1` TỒN TẠI trong mảng (không lòi ra ngoài rìa phải).
 */
function getBlackKeyPositions(totalKeys: number) {
  const positions: Array<{ centerPercent: number; animationDelay: number; whiteIndex: number }> = [];

  for (let i = 0; i < totalKeys; i++) {
    const whiteIndexInOctave = i % 7;
    const hasBlackKeyOnRight = BLACK_KEY_OCTAVE_INDICES.includes(whiteIndexInOctave);
    const hasNextWhiteKey = i + 1 < totalKeys;

    if (hasBlackKeyOnRight && hasNextWhiteKey) {
      // Tâm phím đen = đường BIÊN giữa phím `i` và phím `i+1`
      // = (i + 1) / totalKeys * 100%
      // Vì các phím trắng dính liền nhau (không có gap), phím đen nằm chồng lên
      // đường biên này, với một nửa nằm trong phím i, nửa kia nằm trong phím i+1.
      const centerPercent = ((i + 1) / totalKeys) * 100;

      positions.push({
        centerPercent,
        whiteIndex: i,
        animationDelay: FALL_DELAY + 0.4 + (i * 0.08),
      });
    }
  }
  return positions;
}

function Keyboard({ chars, side }: { chars: string[]; side: "left" | "right" }) {
  // Dùng percentage từ getBlackKeyPositions để căn giữa chính xác phím đen
  // giữa 2 phím trắng ở mọi kích thước màn hình.
  //
  // Cấu trúc: dùng CSS Grid với `grid-template-columns: repeat(N, 1fr)` để phím trắng
  // LUÔN chia đều 100% chiều rộng container (bất kể clamp). Phím đen overlay được
  // absolute theo % của container → canh giữa chính xác biên giữa 2 phím trắng.
  const blackKeys = getBlackKeyPositions(chars.length);

  return (
    <div className="relative w-full">
      {/* Hàng phím trắng + chữ - dùng CSS Grid để phím trắng chia đều 100% width */}
      <div
        className="grid w-full"
        style={{
          gridTemplateColumns: `repeat(${chars.length}, 1fr)`,
          columnGap: "0px",
        }}
      >
        {chars.map((char, index) => (
          <PianoKey
            key={`${side}-${index}`}
            char={char}
            index={index}
            side={side}
            animationDelay={
              side === "left"
                ? FALL_DELAY + (index * 0.08)
                : FALL_DELAY + ((chars.length - 1 - index) * 0.08)
            }
          />
        ))}
      </div>

      {/* Hàng phím đen overlay - căn giữa chính xác giữa 2 phím trắng bằng % */}
      <div
        className="pointer-events-none absolute"
        style={{
          top: "calc(clamp(2.2rem, 5vw, 3.5rem) + 0px)",
          left: 0,
          right: 0,
          height: "clamp(3rem, 7vw, 4.8rem)",
        }}
      >
        {blackKeys.map((bk, idx) => (
          <BlackKey
            key={`${side}-black-${idx}`}
            side={side}
            animationDelay={bk.animationDelay}
            totalKeys={chars.length}
            centerPercent={bk.centerPercent}
          />
        ))}
      </div>
    </div>
  );
}

function LandingContent() {
  return (
    <section className="relative flex min-h-[calc(100vh-64px)] w-full flex-col items-center justify-center overflow-hidden bg-gradient-to-b from-white via-neutral-50 to-neutral-100 px-4 dark:from-black dark:via-neutral-950 dark:to-neutral-900">

      {/* Background decoration */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div className="absolute -left-32 -top-32 h-80 w-80 rounded-full bg-neutral-500/5 blur-3xl dark:bg-neutral-500/10" />
        <div className="absolute -right-32 -bottom-32 h-80 w-80 rounded-full bg-neutral-500/5 blur-3xl dark:bg-neutral-500/10" />
      </div>

      {/* Main Title + Piano Keyboard */}
      <motion.div
        className="relative z-10 flex items-end justify-center"
        style={{ letterSpacing: "0.25rem" }}
        initial={{ pointerEvents: "none" }}
        animate={{ pointerEvents: "auto" }}
        transition={{ delay: 2.5 }}
      >
        <Keyboard chars={leftWord} side="left" />
        <div className="w-8 sm:w-16 md:w-24" />
        <Keyboard chars={rightWord} side="right" />
      </motion.div>

      {/* Hint */}
      <motion.p
        className="absolute bottom-12 mt-12 text-sm text-neutral-400 dark:text-neutral-500"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: FALL_DELAY + 1.2, duration: 0.6 }}
      >
        Hover to play notes
      </motion.p>
    </section>
  );
}

export default function LandingPage() {
  return (
    <div className="flex w-full flex-col font-sans">
      <LandingRedirectGuard />
      <LandingContent />
    </div>
  );
}