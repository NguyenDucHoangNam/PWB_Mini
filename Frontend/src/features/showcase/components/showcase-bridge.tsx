"use client";

import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { useTranslations } from "next-intl";
import { Reveal } from "@/components/marketing/section-primitives";

const CASE_KEYS = ["library", "voiceTag", "liveRoom"] as const;

/**
 * The door to the engineering write-up. Inverted on purpose: the colour flip is what tells you
 * this is a different destination and not the next section of the guide.
 */
export function ShowcaseBridge() {
  const t = useTranslations("features.bridge");
  const tCase = useTranslations("features.engineering");

  return (
    <section className="w-full bg-foreground px-5 py-24 text-background sm:px-8 sm:py-28">
      <Reveal className="mx-auto w-full max-w-6xl">
        <span className="inline-flex items-center gap-3 font-mono text-xs uppercase tracking-[0.22em] text-background/60">
          <span aria-hidden="true" className="h-px w-7 bg-background/30" />
          {t("eyebrow")}
        </span>

        <div className="mt-6 grid gap-10 lg:grid-cols-[1.15fr_1fr] lg:gap-16">
          <div>
            <h2 className="max-w-2xl text-balance font-heading text-3xl font-semibold tracking-tight sm:text-4xl md:text-[2.75rem] md:leading-[1.1]">
              {t("title")}
            </h2>
            <p className="mt-5 max-w-xl text-pretty text-base leading-relaxed text-background/70">
              {t("lead")}
            </p>

            <Link
              href="/features/technical"
              className="group mt-9 inline-flex items-center gap-3 rounded-full bg-background px-7 py-3.5 text-sm font-semibold text-foreground beat-16th transition-opacity hover:opacity-85"
            >
              {t("cta")}
              <ArrowRight
                aria-hidden="true"
                className="size-4 beat-16th transition-transform group-hover:translate-x-1"
              />
            </Link>
          </div>

          {/* A table of contents for the other page, so the click is informed rather than blind. */}
          <ul className="border-t border-background/15">
            {CASE_KEYS.map((key, index) => (
              <li key={key} className="border-b border-background/15">
                <Link
                  href={`/features/technical#${key}`}
                  className="group flex items-start gap-5 py-5 beat-16th transition-colors hover:text-background"
                >
                  <span className="font-mono text-xs leading-6 text-background/45">
                    {String(index + 1).padStart(2, "0")}
                  </span>
                  <span className="flex-1 text-sm leading-6 text-background/75 beat-16th transition-colors group-hover:text-background sm:text-base">
                    {tCase(`${key}.title`)}
                  </span>
                  <ArrowRight
                    aria-hidden="true"
                    className="mt-1 size-4 shrink-0 text-background/35 beat-16th transition-transform group-hover:translate-x-1 group-hover:text-background"
                  />
                </Link>
              </li>
            ))}
          </ul>
        </div>
      </Reveal>
    </section>
  );
}
