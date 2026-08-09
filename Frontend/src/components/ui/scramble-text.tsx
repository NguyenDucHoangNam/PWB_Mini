"use client";

import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { cn } from "@/lib/utils";

const GLYPHS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789#%&@$*<>/\\";

const DEFAULTS = {
  startDelay: 320,
  charDelay: 62,
  glyphInterval: 55,
};

// useLayoutEffect runs before paint, so the first scrambled frame replaces the
// server-rendered answer without it ever reaching the screen. On the server the
// hook does not run at all, which React warns about — hence the swap.
const useIsomorphicLayoutEffect = typeof window === "undefined" ? useEffect : useLayoutEffect;

type Cell = { char: string; settled: boolean };

type ScrambleTextProps = {
  text: string;
  className?: string;
  startDelay?: number;
  charDelay?: number;
  glyphInterval?: number;
};

function settledCells(text: string): Cell[] {
  return [...text].map((char) => ({ char, settled: true }));
}

export function ScrambleText({
  text,
  className,
  startDelay = DEFAULTS.startDelay,
  charDelay = DEFAULTS.charDelay,
  glyphInterval = DEFAULTS.glyphInterval,
}: ScrambleTextProps) {
  // The real string is what renders on the server and before hydration, so a reader
  // with no JS still gets the name rather than a screenful of noise.
  const [cells, setCells] = useState<Cell[]>(() => settledCells(text));
  const frameRef = useRef(0);

  useIsomorphicLayoutEffect(() => {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      setCells(settledCells(text));
      return;
    }

    const chars = [...text];
    // Spaces never churn: holding the word break steady keeps the shape of the name
    // legible while the letters are still noise.
    const deadlineFor = (index: number) => startDelay + (index + 1) * charDelay;
    const total = deadlineFor(chars.length - 1);

    const frameAt = (elapsed: number): Cell[] =>
      chars.map((char, index) => {
        if (char === " ") return { char, settled: true };
        if (elapsed >= deadlineFor(index)) return { char, settled: true };
        return { char: GLYPHS[Math.floor(Math.random() * GLYPHS.length)], settled: false };
      });

    // Painted in this same commit, so the real string never flashes before the churn.
    setCells(frameAt(0));

    // The clock starts on the first delivered frame, not at mount. A tab that loads in
    // the background gets no frames at all, and would otherwise "play" the whole decode
    // while hidden and settle before anyone looked at it.
    let start: number | null = null;
    let lastSwap = 0;

    const tick = (now: number) => {
      start ??= now;
      const elapsed = now - start;

      if (elapsed - lastSwap >= glyphInterval) {
        lastSwap = elapsed;
        setCells(frameAt(elapsed));
      }

      if (elapsed < total) {
        frameRef.current = requestAnimationFrame(tick);
      } else {
        setCells(settledCells(text));
      }
    };

    frameRef.current = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frameRef.current);
  }, [text, startDelay, charDelay, glyphInterval]);

  return (
    <span className={cn("font-mono tabular-nums", className)}>
      <span className="sr-only">{text}</span>
      <span aria-hidden="true">
        {cells.map((cell, index) => (
          <span
            key={index}
            className={cn(
              "transition-opacity duration-150",
              cell.settled ? "opacity-100" : "opacity-45",
            )}
          >
            {cell.char}
          </span>
        ))}
      </span>
    </span>
  );
}
