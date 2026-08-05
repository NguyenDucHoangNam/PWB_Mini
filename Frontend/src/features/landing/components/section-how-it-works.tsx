"use client";

import { motion, type Variants } from "framer-motion";
import { useTranslations } from "next-intl";

const STEPS = [
  { titleKey: "step1Title", descKey: "step1Description", number: "01" },
  { titleKey: "step2Title", descKey: "step2Description", number: "02" },
  { titleKey: "step3Title", descKey: "step3Description", number: "03" },
] as const;

const containerVariants: Variants = {
  hidden: {},
  visible: {
    transition: { staggerChildren: 0.2 },
  },
};

const stepVariants: Variants = {
  hidden: { opacity: 0, y: 40 },
  visible: {
    opacity: 1,
    y: 0,
    transition: { duration: 0.6, ease: "easeOut" },
  },
};

export function SectionHowItWorks() {
  const t = useTranslations("landing.howItWorks");

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
          className="relative grid gap-8 sm:gap-0 sm:grid-cols-3"
          variants={containerVariants}
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true, margin: "-60px" }}
        >
          <div className="pointer-events-none absolute inset-x-0 top-6 hidden h-px bg-neutral-200 sm:block dark:bg-neutral-800" />

          {STEPS.map(({ titleKey, descKey, number }) => (
            <motion.div
              key={number}
              variants={stepVariants}
              className="relative flex flex-col items-center text-center sm:px-6"
            >
              <div className="relative z-10 mb-5 flex size-12 items-center justify-center rounded-full border-2 border-neutral-300 bg-white font-mono text-sm font-bold text-black dark:border-neutral-600 dark:bg-neutral-900 dark:text-white">
                {number}
              </div>

              <h3 className="text-lg font-semibold text-black dark:text-white">
                {t(titleKey)}
              </h3>
              <p className="mt-2 max-w-[240px] text-sm leading-relaxed text-neutral-500 dark:text-neutral-400">
                {t(descKey)}
              </p>
            </motion.div>
          ))}
        </motion.div>
      </div>
    </section>
  );
}
