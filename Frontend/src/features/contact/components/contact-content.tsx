"use client";

import { useState } from "react";
import { Check, Clock, Copy, Globe, Mail, MapPin } from "lucide-react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Eyebrow } from "@/components/marketing/section-primitives";
import { CONTACT_EMAIL } from "../lib/contact-details";

const INFO_KEYS = [
  { key: "response", icon: Clock },
  { key: "hours", icon: MapPin },
  { key: "languages", icon: Globe },
] as const;

export function ContactContent() {
  const t = useTranslations("contact");
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(CONTACT_EMAIL);
      setCopied(true);
      toast.success(t("copied"));
      // Revert after a beat so the button reads as a confirmation, not a new state.
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      toast.error(t("copyFailed"));
    }
  };

  return (
    <section className="relative flex w-full flex-1 items-center overflow-hidden bg-background px-5 py-16 font-sans sm:px-8">
      {/* Dotted drafting field, faded out at the edges so it never competes with the card. */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 opacity-[0.5] dark:opacity-30"
        style={{
          backgroundImage: "radial-gradient(var(--border) 1px, transparent 1px)",
          backgroundSize: "26px 26px",
          maskImage: "radial-gradient(ellipse 70% 60% at 50% 40%, black, transparent 80%)",
        }}
      />

      <div className="relative mx-auto grid w-full max-w-5xl items-center gap-10 lg:grid-cols-[1fr_minmax(0,22rem)] lg:gap-14">
        <div className="min-w-0">
          <Eyebrow>{t("eyebrow")}</Eyebrow>

          <h1 className="mt-5 text-balance font-heading text-3xl font-semibold tracking-tight text-foreground sm:text-4xl md:text-5xl">
            {t("title")}
          </h1>

          <p className="mt-5 max-w-xl text-pretty text-base leading-relaxed text-muted-foreground">
            {t("lead")}
          </p>

          <div className="mt-8 flex flex-col gap-3 sm:flex-row sm:items-center">
            <a
              href={`mailto:${CONTACT_EMAIL}`}
              className="inline-flex h-11 items-center justify-center gap-2.5 rounded-full bg-foreground px-6 text-sm font-medium text-background beat-16th transition-opacity hover:opacity-85"
            >
              <Mail aria-hidden="true" className="size-4" />
              {t("emailCta")}
            </a>
            <button
              type="button"
              onClick={handleCopy}
              className="inline-flex h-11 items-center justify-center gap-2.5 rounded-full border border-border bg-background px-6 text-sm font-medium text-foreground beat-16th transition-colors hover:border-foreground/40"
            >
              {copied ? (
                <Check aria-hidden="true" className="size-4" />
              ) : (
                <Copy aria-hidden="true" className="size-4" />
              )}
              {copied ? t("copiedShort") : t("copyCta")}
            </button>
          </div>
        </div>

        <div className="rounded-2xl border border-border bg-card p-6 sm:p-7">
          <span className="font-mono text-[0.7rem] uppercase tracking-[0.2em] text-muted-foreground">
            {t("infoTitle")}
          </span>

          <a
            href={`mailto:${CONTACT_EMAIL}`}
            className="mt-4 flex items-center gap-3 rounded-xl border border-border bg-muted/40 px-3.5 py-3 beat-16th transition-colors hover:border-foreground/30"
          >
            <Mail aria-hidden="true" className="size-4 shrink-0 text-muted-foreground" />
            <span className="min-w-0 truncate font-mono text-xs text-foreground">
              {CONTACT_EMAIL}
            </span>
          </a>

          <dl className="mt-2">
            {INFO_KEYS.map(({ key, icon: Icon }) => (
              <div key={key} className="flex gap-3 border-b border-border py-4 last:border-b-0">
                <Icon aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
                <div className="min-w-0">
                  <dt className="font-mono text-[0.7rem] uppercase tracking-[0.18em] text-muted-foreground">
                    {t(`${key}Label`)}
                  </dt>
                  <dd className="mt-1 text-sm leading-relaxed text-foreground">
                    {t(`${key}Value`)}
                  </dd>
                </div>
              </div>
            ))}
          </dl>
        </div>
      </div>
    </section>
  );
}
