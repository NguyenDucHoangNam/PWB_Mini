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
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      toast.error(t("copyFailed"));
    }
  };

  return (
    <section className="relative flex min-h-[calc(100dvh-5rem)] w-full flex-1 items-center overflow-hidden bg-[#e0e5ec] px-5 py-16 font-sans dark:bg-[#1e222b] sm:px-8 transition-colors border-none">
      <div className="relative mx-auto grid w-full max-w-5xl items-center gap-10 lg:grid-cols-[1fr_minmax(0,22rem)] lg:gap-14">
        <div className="min-w-0">
          <Eyebrow>{t("eyebrow")}</Eyebrow>

          <h1 className="mt-5 text-balance font-heading text-3xl font-bold tracking-tight text-slate-900 dark:text-slate-100 sm:text-4xl md:text-5xl">
            {t("title")}
          </h1>

          <p className="mt-5 max-w-xl text-pretty text-base leading-relaxed text-slate-600 dark:text-slate-300">
            {t("lead")}
          </p>

          <div className="mt-8 flex flex-col gap-4 sm:flex-row sm:items-center">
            <a
              href={`mailto:${CONTACT_EMAIL}`}
              className="neu-button-primary inline-flex h-12 items-center justify-center gap-2.5 rounded-2xl bg-indigo-600 px-7 text-sm font-bold text-white shadow-neu-raised-sm hover:bg-indigo-700 focus-visible:outline-2 focus-visible:outline-indigo-600 focus-visible:outline-offset-2 transition-all"
            >
              <Mail aria-hidden="true" className="size-4" />
              {t("emailCta")}
            </a>
            <button
              type="button"
              onClick={handleCopy}
              className="neu-button inline-flex h-12 items-center justify-center gap-2.5 rounded-2xl bg-[#e0e5ec] px-7 text-sm font-bold text-slate-800 dark:bg-[#1e222b] dark:text-slate-200 focus-visible:outline-2 focus-visible:outline-indigo-600 focus-visible:outline-offset-2 transition-all"
            >
              {copied ? (
                <Check aria-hidden="true" className="size-4 text-emerald-600 dark:text-emerald-400" />
              ) : (
                <Copy aria-hidden="true" className="size-4 text-slate-600 dark:text-slate-300" />
              )}
              {copied ? t("copiedShort") : t("copyCta")}
            </button>
          </div>
        </div>

        <div className="neu-raised rounded-3xl border-none bg-[#e0e5ec] p-7 sm:p-8 dark:bg-[#1e222b]">
          <span className="font-mono text-xs font-bold uppercase tracking-[0.2em] text-indigo-600 dark:text-indigo-400">
            {t("infoTitle")}
          </span>

          <a
            href={`mailto:${CONTACT_EMAIL}`}
            className="neu-pressed-sm mt-5 flex items-center gap-3 rounded-2xl bg-[#e0e5ec] px-4 py-3.5 dark:bg-[#1e222b] transition-all hover:scale-[1.01]"
          >
            <Mail aria-hidden="true" className="size-4 shrink-0 text-indigo-600 dark:text-indigo-400" />
            <span className="min-w-0 truncate font-mono text-xs font-bold text-slate-800 dark:text-slate-200">
              {CONTACT_EMAIL}
            </span>
          </a>

          <dl className="mt-4">
            {INFO_KEYS.map(({ key, icon: Icon }) => (
              <div key={key} className="flex gap-3.5 border-b border-slate-300/40 dark:border-slate-700/40 py-4.5 last:border-b-0">
                <Icon aria-hidden="true" className="mt-0.5 size-4.5 shrink-0 text-indigo-600 dark:text-indigo-400" />
                <div className="min-w-0">
                  <dt className="font-mono text-[0.7rem] font-bold uppercase tracking-[0.18em] text-slate-500 dark:text-slate-400">
                    {t(`${key}Label`)}
                  </dt>
                  <dd className="mt-1 text-sm font-semibold leading-relaxed text-slate-800 dark:text-slate-200">
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
