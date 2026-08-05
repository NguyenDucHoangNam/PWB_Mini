"use client";

import { motion, type Variants } from "framer-motion";
import { useTranslations } from "next-intl";
import { Upload, Tag, Share2, ArrowRight } from "lucide-react";

const STEPS = [
  { labelKey: "step1Label", descKey: "step1Description", icon: Upload },
  { labelKey: "step2Label", descKey: "step2Description", icon: Tag },
  { labelKey: "step3Label", descKey: "step3Description", icon: Share2 },
] as const;

const containerVariants: Variants = {
  hidden: {},
  visible: {
    transition: { staggerChildren: 0.2 },
  },
};

const stepVariants: Variants = {
  hidden: { opacity: 0, scale: 0.85 },
  visible: {
    opacity: 1,
    scale: 1,
    transition: { duration: 0.5, ease: "easeOut" },
  },
};

const arrowVariants: Variants = {
  hidden: { opacity: 0, x: -10 },
  visible: {
    opacity: 1,
    x: 0,
    transition: { duration: 0.4, ease: "easeOut" },
  },
};

export function SectionDemo() {
  const t = useTranslations("landing.demo");

  return (
    <section className="relative w-full overflow-hidden bg-white px-4 py-24 sm:px-6 sm:py-32 md:px-8 dark:bg-black">
      <div className="mx-auto max-w-4xl">
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
        </motion.div>

        <motion.div
          className="flex flex-col items-center gap-6 sm:flex-row sm:justify-center sm:gap-0"
          variants={containerVariants}
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true, margin: "-60px" }}
        >
          {STEPS.map(({ labelKey, descKey, icon: Icon }, index) => (
            <div key={labelKey} className="flex flex-col items-center sm:flex-row">
              <motion.div
                variants={stepVariants}
                className="group flex flex-col items-center gap-3 rounded-2xl border border-neutral-200 bg-neutral-50 px-8 py-6 transition-all duration-300 hover:border-neutral-400 hover:shadow-md sm:px-10 sm:py-8 dark:border-neutral-800 dark:bg-neutral-900 dark:hover:border-neutral-600"
              >
                <div className="flex size-14 items-center justify-center rounded-full border border-neutral-200 bg-white transition-colors group-hover:border-neutral-300 dark:border-neutral-700 dark:bg-neutral-800 dark:group-hover:border-neutral-600">
                  <Icon className="size-6 text-neutral-700 dark:text-neutral-300" aria-hidden="true" />
                </div>
                <span className="text-base font-semibold text-black dark:text-white">
                  {t(labelKey)}
                </span>
                <span className="max-w-[160px] text-center text-xs leading-relaxed text-neutral-500 dark:text-neutral-400">
                  {t(descKey)}
                </span>
              </motion.div>

              {index < STEPS.length - 1 && (
                <motion.div
                  variants={arrowVariants}
                  className="flex items-center justify-center py-2 sm:px-4 sm:py-0"
                >
                  <ArrowRight className="size-5 rotate-90 text-neutral-300 sm:rotate-0 dark:text-neutral-600" aria-hidden="true" />
                </motion.div>
              )}
            </div>
          ))}
        </motion.div>
      </div>
    </section>
  );
}
