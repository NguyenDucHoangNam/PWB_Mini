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

/* Fades the strip into the page edges instead of cutting the words off mid-stroke. */
const EDGE_MASK =
  "linear-gradient(to right, transparent, black 5rem, black calc(100% - 5rem), transparent)";

export function SectionTicker() {
  const t = useTranslations("landing.ticker");
  const prefersReducedMotion = useReducedMotion();
  const items = ITEM_KEYS.map((key) => t(key));

  return (
    <div
      className="relative w-full overflow-hidden border-y border-border bg-card py-4"
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
        {/* Two identical runs: shifting by exactly half the track loops seamlessly. */}
        {[0, 1].map((copy) => (
          <div key={copy} className="flex shrink-0" aria-hidden={copy === 1}>
            {items.map((label) => (
              <span key={label} className="flex shrink-0 items-center">
                <span className="whitespace-nowrap px-6 font-mono text-xs uppercase tracking-[0.18em] text-muted-foreground">
                  {label}
                </span>
                <span aria-hidden="true" className="size-1 shrink-0 rotate-45 bg-muted-foreground/40" />
              </span>
            ))}
          </div>
        ))}
      </motion.div>
    </div>
  );
}
