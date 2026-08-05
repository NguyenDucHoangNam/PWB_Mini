"use client";

import { motion } from "framer-motion";
import { useTranslations } from "next-intl";

export function SectionTagline() {
  const t = useTranslations("landing.tagline");

  return (
    <section className="relative w-full overflow-hidden bg-white px-4 py-24 sm:px-6 sm:py-32 md:px-8 dark:bg-black">
      <div className="pointer-events-none absolute inset-0">
        <div className="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-neutral-300 to-transparent dark:via-neutral-700" />
      </div>

      <div className="mx-auto max-w-3xl text-center">
        <motion.h2
          className="text-3xl font-bold tracking-tight text-black sm:text-4xl md:text-5xl lg:text-6xl dark:text-white"
          initial={{ opacity: 0, y: 40 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: "-100px" }}
          transition={{ duration: 0.7, ease: "easeOut" }}
        >
          {t("headline")}
        </motion.h2>

        <motion.p
          className="mt-5 text-base leading-relaxed text-neutral-500 sm:mt-6 sm:text-lg md:text-xl dark:text-neutral-400"
          initial={{ opacity: 0, y: 30 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: "-100px" }}
          transition={{ duration: 0.7, delay: 0.15, ease: "easeOut" }}
        >
          {t("subheadline")}
        </motion.p>
      </div>

      <div className="pointer-events-none absolute inset-0">
        <div className="absolute inset-x-0 bottom-0 h-px bg-gradient-to-r from-transparent via-neutral-300 to-transparent dark:via-neutral-700" />
      </div>
    </section>
  );
}
