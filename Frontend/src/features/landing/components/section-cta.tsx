"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Eyebrow, Reveal } from "./section-primitives";

const WHITE_KEY_COUNT = 21;
/* Positions of the black keys inside each group of seven white keys. */
const BLACK_KEY_SLOTS = [0, 1, 3, 4, 5];

const FADE_UP_MASK = "linear-gradient(to top, black 35%, transparent)";

export function SectionCta() {
  const t = useTranslations("landing.cta");

  return (
    <section className="relative w-full overflow-hidden bg-background px-5 pt-24 pb-44 sm:px-8 sm:pt-28 sm:pb-52 md:pt-32">
      <Reveal className="relative z-10 mx-auto flex max-w-2xl flex-col items-center text-center">
        <Eyebrow>{t("eyebrow")}</Eyebrow>

        <h2 className="mt-5 text-balance font-heading text-3xl font-semibold tracking-tight text-foreground sm:text-4xl md:text-5xl">
          {t("headline")}
        </h2>
        <p className="mt-5 text-pretty text-base leading-relaxed text-muted-foreground sm:text-lg">
          {t("subheadline")}
        </p>

        <div className="mt-10 flex w-full flex-col items-center gap-3 sm:w-auto sm:flex-row sm:gap-4">
          <Button
            render={<Link href="/register" />}
            nativeButton={false}
            size="lg"
            className="h-11 w-full rounded-xl px-6 text-sm font-semibold sm:w-auto sm:min-w-[190px]"
          >
            {t("getStarted")}
          </Button>
          <Button
            render={<Link href="/features" />}
            variant="outline"
            nativeButton={false}
            size="lg"
            className="h-11 w-full rounded-xl px-6 text-sm font-semibold sm:w-auto sm:min-w-[190px]"
          >
            {t("exploreFeatures")}
          </Button>
        </div>

        <p className="mt-8 font-mono text-xs uppercase tracking-[0.2em] text-muted-foreground">
          {t("footnote")}
        </p>
      </Reveal>

      <KeyboardFooter />
    </section>
  );
}

/* Closing bookend to the hero keyboard: a silhouette of keys rising out of the bottom edge. */
function KeyboardFooter() {
  return (
    <div
      aria-hidden="true"
      className="pointer-events-none absolute inset-x-0 bottom-0 h-32 select-none sm:h-36"
      style={{ maskImage: FADE_UP_MASK, WebkitMaskImage: FADE_UP_MASK }}
    >
      <div className="flex h-full w-full items-stretch opacity-70">
        {Array.from({ length: WHITE_KEY_COUNT }, (_, index) => (
          <div key={index} className="relative flex-1">
            <div className="key-white h-full w-full" />
            {BLACK_KEY_SLOTS.includes(index % 7) && (
              <div className="key-black absolute right-0 top-0 z-10 h-3/5 w-3/5 translate-x-1/2" />
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
