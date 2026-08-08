"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Eyebrow, Reveal } from "@/components/marketing/section-primitives";

export function TechnicalOutro() {
  const t = useTranslations("features.technical.outro");

  return (
    <section className="w-full border-t border-border bg-muted/40 px-5 py-24 sm:px-8 sm:py-28">
      <Reveal className="mx-auto flex max-w-2xl flex-col items-center text-center">
        <Eyebrow>{t("eyebrow")}</Eyebrow>

        <h2 className="mt-5 text-balance font-heading text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">
          {t("title")}
        </h2>
        <p className="mt-5 text-pretty text-base leading-relaxed text-muted-foreground">
          {t("lead")}
        </p>

        <div className="mt-10 flex w-full flex-col items-center gap-3 sm:w-auto sm:flex-row sm:gap-4">
          <Button
            render={<Link href="/features" />}
            nativeButton={false}
            size="lg"
            className="h-11 w-full rounded-xl px-6 text-sm font-semibold sm:w-auto sm:min-w-[190px]"
          >
            {t("primaryCta")}
          </Button>
          <Button
            render={<Link href="/register" />}
            variant="outline"
            nativeButton={false}
            size="lg"
            className="h-11 w-full rounded-xl px-6 text-sm font-semibold sm:w-auto sm:min-w-[190px]"
          >
            {t("secondaryCta")}
          </Button>
        </div>
      </Reveal>
    </section>
  );
}
