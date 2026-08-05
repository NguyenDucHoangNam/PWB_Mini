"use client";

import Link from "next/link";
import { motion } from "framer-motion";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";

export function SectionCta() {
  const t = useTranslations("landing.cta");

  return (
    <section className="relative w-full overflow-hidden bg-neutral-50 px-4 py-24 sm:px-6 sm:py-32 md:px-8 dark:bg-neutral-950">
      <div className="pointer-events-none absolute inset-0 overflow-hidden">
        <div className="absolute left-1/2 top-1/2 h-[400px] w-[600px] -translate-x-1/2 -translate-y-1/2 rounded-full bg-neutral-200/40 blur-[100px] dark:bg-neutral-800/20" />
      </div>

      <div className="relative z-10 mx-auto max-w-2xl text-center">
        <motion.h2
          className="text-3xl font-bold tracking-tight text-black sm:text-4xl md:text-5xl dark:text-white"
          initial={{ opacity: 0, y: 40 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: "-80px" }}
          transition={{ duration: 0.7, ease: "easeOut" }}
        >
          {t("headline")}
        </motion.h2>

        <motion.p
          className="mt-4 text-base text-neutral-500 sm:text-lg dark:text-neutral-400"
          initial={{ opacity: 0, y: 30 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: "-80px" }}
          transition={{ duration: 0.7, delay: 0.1, ease: "easeOut" }}
        >
          {t("subheadline")}
        </motion.p>

        <motion.div
          className="mt-10 flex flex-col items-center gap-3 sm:flex-row sm:justify-center sm:gap-4"
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: "-60px" }}
          transition={{ duration: 0.6, delay: 0.2, ease: "easeOut" }}
        >
          <Link href="/register">
            <Button
              variant="default"
              size="lg"
              className="min-w-[200px] text-sm font-semibold sm:min-w-[180px]"
            >
              {t("getStarted")}
            </Button>
          </Link>
          <Link href="/features">
            <Button
              variant="outline"
              size="lg"
              className="min-w-[200px] text-sm font-semibold sm:min-w-[180px]"
            >
              {t("exploreFeatures")}
            </Button>
          </Link>
        </motion.div>
      </div>
    </section>
  );
}
