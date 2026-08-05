"use client";

import { motion, type Variants } from "framer-motion";
import { useTranslations } from "next-intl";
import { ListMusic, Mic, Radio } from "lucide-react";

const FEATURES = [
  { key: "playlist" as const, icon: ListMusic },
  { key: "voiceTags" as const, icon: Mic },
  { key: "liveroom" as const, icon: Radio },
];

const containerVariants: Variants = {
  hidden: {},
  visible: {
    transition: { staggerChildren: 0.15 },
  },
};

const cardVariants: Variants = {
  hidden: { opacity: 0, y: 50 },
  visible: {
    opacity: 1,
    y: 0,
    transition: { duration: 0.6, ease: "easeOut" },
  },
};

export function SectionFeatures() {
  const t = useTranslations("landing.features");

  return (
    <section className="relative w-full overflow-hidden bg-neutral-50 px-4 py-24 sm:px-6 sm:py-32 md:px-8 dark:bg-neutral-950">
      <div className="mx-auto max-w-5xl">
        <motion.div
          className="mb-16 text-center"
          initial={{ opacity: 0, y: 30 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: "-80px" }}
          transition={{ duration: 0.6 }}
        >
          <h2 className="text-2xl font-bold tracking-tight text-black sm:text-3xl md:text-4xl dark:text-white">
            {t("sectionTitle")}
          </h2>
          <p className="mt-3 text-sm text-neutral-500 sm:text-base dark:text-neutral-400">
            {t("sectionSubtitle")}
          </p>
        </motion.div>

        <motion.div
          className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3"
          variants={containerVariants}
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true, margin: "-60px" }}
        >
          {FEATURES.map(({ key, icon: Icon }) => (
            <motion.div
              key={key}
              variants={cardVariants}
              className="group relative rounded-xl border border-neutral-200 bg-white p-6 transition-all duration-300 hover:border-neutral-400 hover:shadow-lg sm:p-8 dark:border-neutral-800 dark:bg-neutral-900 dark:hover:border-neutral-600"
            >
              <div className="mb-4 flex size-11 items-center justify-center rounded-lg border border-neutral-200 bg-neutral-50 transition-colors group-hover:border-neutral-300 dark:border-neutral-700 dark:bg-neutral-800 dark:group-hover:border-neutral-600">
                <Icon className="size-5 text-neutral-700 dark:text-neutral-300" aria-hidden="true" />
              </div>

              <h3 className="text-lg font-semibold text-black dark:text-white">
                {t(`${key}.title`)}
              </h3>
              <p className="mt-2 text-sm leading-relaxed text-neutral-500 dark:text-neutral-400">
                {t(`${key}.description`)}
              </p>

              <div className="pointer-events-none absolute inset-0 rounded-xl opacity-0 transition-opacity duration-300 group-hover:opacity-100">
                <div className="absolute inset-x-4 bottom-0 h-px bg-gradient-to-r from-transparent via-neutral-400 to-transparent dark:via-neutral-500" />
              </div>
            </motion.div>
          ))}
        </motion.div>
      </div>
    </section>
  );
}
