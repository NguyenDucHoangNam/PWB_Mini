"use client";

import { motion, useReducedMotion } from "framer-motion";
import { useTranslations } from "next-intl";

const ITEM_KEYS = [
  "item1",
  "item2",
  "item3",
  "item4",
  "item5",
  "item6",
  "item7",
  "item8",
  "item9",
] as const;

const SCROLL_DURATION_SECONDS = 45;

const EDGE_MASK =
  "linear-gradient(to right, transparent, black 5rem, black calc(100% - 5rem), transparent)";

export function SectionTicker() {
  const t = useTranslations("landing.ticker");
  const prefersReducedMotion = useReducedMotion();
  const items = ITEM_KEYS.map((key) => t(key));

  /* The trough stays on the outer element and the fade on an inner one, so the pressed
     top and bottom edges run the full width instead of fading out with the text. */
  return (
    <div className="neu-trough relative w-full border-none bg-[var(--neu-surface)] py-6">
      <div
        className="w-full overflow-hidden"
        style={{ maskImage: EDGE_MASK, WebkitMaskImage: EDGE_MASK }}
      >
        <motion.div
          className="flex w-max"
          animate={prefersReducedMotion ? undefined : { x: ["0%", "-50%"] }}
          transition={{
            duration: SCROLL_DURATION_SECONDS,
            ease: "linear",
            repeat: Infinity,
          }}
        >
          {[0, 1].map((copy) => (
            <div key={copy} className="flex shrink-0" aria-hidden={copy === 1}>
              {items.map((label) => (
                <span key={label} className="flex shrink-0 items-center">
                  <span className="whitespace-nowrap px-7 font-mono text-xs font-semibold uppercase tracking-[0.18em] text-slate-700 dark:text-slate-300">
                    {label}
                  </span>
                  <span
                    aria-hidden="true"
                    className="size-1.5 shrink-0 rounded-full bg-indigo-600 dark:bg-indigo-400"
                  />
                </span>
              ))}
            </div>
          ))}
        </motion.div>
      </div>
    </div>
  );
}
