"use client";

import { useEffect, useRef, useState } from "react";
import { motion } from "framer-motion";
import { useTranslations } from "next-intl";

const STATS_DATA = [
  { key: "producers" as const, value: 500 },
  { key: "beats" as const, value: 10000 },
  { key: "sessions" as const, value: 1000 },
];

function useCountUp(target: number, isVisible: boolean, duration = 2000) {
  const [count, setCount] = useState(0);
  const hasAnimated = useRef(false);

  useEffect(() => {
    if (!isVisible || hasAnimated.current) return;
    hasAnimated.current = true;

    const startTime = performance.now();
    let frameId: number;

    const animate = (currentTime: number) => {
      const elapsed = currentTime - startTime;
      const progress = Math.min(elapsed / duration, 1);
      const eased = 1 - Math.pow(1 - progress, 3);

      setCount(Math.round(eased * target));

      if (progress < 1) {
        frameId = requestAnimationFrame(animate);
      }
    };

    frameId = requestAnimationFrame(animate);
    return () => cancelAnimationFrame(frameId);
  }, [isVisible, target, duration]);

  return count;
}

function formatNumber(num: number): string {
  if (num >= 1000) {
    return `${(num / 1000).toFixed(num % 1000 === 0 ? 0 : 1)}k`;
  }
  return num.toString();
}

function StatItem({ value, labelKey, isVisible, t }: {
  value: number;
  labelKey: string;
  isVisible: boolean;
  t: (key: string) => string;
}) {
  const count = useCountUp(value, isVisible);

  return (
    <div className="flex flex-col items-center gap-2">
      <span className="font-mono text-4xl font-bold tracking-tight text-black sm:text-5xl md:text-6xl dark:text-white">
        {formatNumber(count)}+
      </span>
      <span className="text-sm uppercase tracking-wider text-neutral-500 dark:text-neutral-400">
        {t(labelKey)}
      </span>
    </div>
  );
}

export function SectionStats() {
  const t = useTranslations("landing.stats");
  const [isVisible, setIsVisible] = useState(false);
  const sectionRef = useRef<HTMLElement>(null);

  useEffect(() => {
    const el = sectionRef.current;
    if (!el) return;

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setIsVisible(true);
        }
      },
      { threshold: 0.3 },
    );

    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  return (
    <section
      ref={sectionRef}
      className="relative w-full overflow-hidden bg-white px-4 py-24 sm:px-6 sm:py-32 md:px-8 dark:bg-black"
    >
      <div className="mx-auto max-w-4xl">
        <motion.div
          className="mb-14 text-center"
          initial={{ opacity: 0, y: 30 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: "-80px" }}
          transition={{ duration: 0.6 }}
        >
          <h2 className="text-2xl font-bold tracking-tight text-black sm:text-3xl md:text-4xl dark:text-white">
            {t("sectionTitle")}
          </h2>
        </motion.div>

        <motion.div
          className="grid grid-cols-1 gap-12 sm:grid-cols-3 sm:gap-8"
          initial={{ opacity: 0 }}
          whileInView={{ opacity: 1 }}
          viewport={{ once: true, margin: "-60px" }}
          transition={{ duration: 0.6, delay: 0.2 }}
        >
          {STATS_DATA.map(({ key, value }) => (
            <StatItem
              key={key}
              value={value}
              labelKey={key}
              isVisible={isVisible}
              t={t}
            />
          ))}
        </motion.div>
      </div>
    </section>
  );
}
