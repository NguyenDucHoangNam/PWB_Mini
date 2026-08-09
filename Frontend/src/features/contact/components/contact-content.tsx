"use client";

import { useState, type ComponentType, type SVGProps } from "react";
import Image from "next/image";
import { Check, Copy, Globe, Mail, Phone } from "lucide-react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Eyebrow } from "@/components/marketing/section-primitives";
import { FacebookIcon, InstagramIcon } from "./brand-icons";
import {
  CONTACT_EMAIL,
  CONTACT_PHONE_DISPLAY,
  CONTACT_PHONE_TEL,
  CONTACT_PORTRAIT,
  CONTACT_SOCIALS,
  type SocialKey,
} from "../lib/contact-details";

type IconComponent = ComponentType<SVGProps<SVGSVGElement>>;

const SOCIAL_ICONS: Record<SocialKey, IconComponent> = {
  facebook: FacebookIcon,
  instagram: InstagramIcon,
  portfolio: Globe,
};

const SURFACE = "bg-[#e0e5ec] dark:bg-[#1e222b]";
const FOCUS_RING =
  "focus-visible:outline-2 focus-visible:outline-indigo-600 focus-visible:outline-offset-2";

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
    <section
      className={`flex w-full flex-1 items-center justify-center px-4 py-10 font-sans transition-colors sm:px-8 ${SURFACE}`}
    >
      <div className={`neu-raised w-full rounded-[2rem] p-5 sm:p-8 lg:p-10 ${SURFACE}`}>
        <div className="grid gap-7 sm:grid-cols-[13rem_minmax(0,1fr)] sm:gap-9 sm:items-center lg:grid-cols-[20rem_minmax(0,1fr)] lg:gap-12">
          <Image
            src={CONTACT_PORTRAIT.src}
            width={CONTACT_PORTRAIT.width}
            height={CONTACT_PORTRAIT.height}
            alt={t("photoAlt")}
            sizes="(min-width: 1024px) 20rem, (min-width: 640px) 13rem, 100vw"
            priority
            className="mx-auto aspect-[10/13] w-full max-w-[20rem] rounded-[1.4rem] object-cover shadow-[6px_6px_14px_var(--neu-dark-shadow)]"
          />

          <div className="min-w-0">
            <Eyebrow>{t("eyebrow")}</Eyebrow>

            <h1 className="mt-4 max-w-3xl text-balance font-heading text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl lg:text-4xl dark:text-slate-100">
              {t.rich("title", {
                partner: (chunks) => (
                  <span className="text-indigo-600 dark:text-indigo-400">{chunks}</span>
                ),
              })}
            </h1>

            {/* The card runs edge to edge, so the prose keeps its own measure — a
                headline stretched across a 2000px screen stops being readable. */}
            <p className="mt-3 max-w-3xl text-pretty text-sm leading-relaxed text-slate-600 dark:text-slate-300">
              {t("partnerNote")}
            </p>

            <ul className="mt-6 grid gap-2.5 sm:grid-cols-2 2xl:grid-cols-3">
              <li className="sm:col-span-2 2xl:col-span-1">
                <ChannelRow
                  href={`mailto:${CONTACT_EMAIL}`}
                  icon={Mail}
                  label={t("emailLabel")}
                  value={CONTACT_EMAIL}
                />
              </li>
              <li>
                <ChannelRow
                  href={`tel:${CONTACT_PHONE_TEL}`}
                  icon={Phone}
                  label={t("phoneLabel")}
                  value={CONTACT_PHONE_DISPLAY}
                />
              </li>
              {CONTACT_SOCIALS.map(({ key, href, handle }) => (
                <li key={key}>
                  <ChannelRow
                    href={href}
                    icon={SOCIAL_ICONS[key]}
                    label={t(`${key}Label`)}
                    value={handle}
                    external
                  />
                </li>
              ))}
            </ul>

            <div className="mt-6 flex flex-col gap-2.5 sm:flex-row sm:items-center">
              <a
                href={`mailto:${CONTACT_EMAIL}`}
                className={`neu-button-primary inline-flex h-11 items-center justify-center gap-2.5 rounded-2xl px-6 text-sm font-bold transition-all ${FOCUS_RING}`}
              >
                <Mail aria-hidden="true" className="size-4" />
                {t("emailCta")}
              </a>
              <button
                type="button"
                onClick={handleCopy}
                className={`neu-button inline-flex h-11 items-center justify-center gap-2.5 rounded-2xl px-6 text-sm font-bold transition-all ${FOCUS_RING}`}
              >
                {copied ? (
                  <Check
                    aria-hidden="true"
                    className="size-4 text-emerald-700 dark:text-emerald-400"
                  />
                ) : (
                  <Copy aria-hidden="true" className="size-4 text-slate-600 dark:text-slate-300" />
                )}
                {copied ? t("copiedShort") : t("copyCta")}
              </button>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

type ChannelRowProps = {
  href: string;
  icon: IconComponent;
  label: string;
  value: string;
  external?: boolean;
};

function ChannelRow({ href, icon: Icon, label, value, external }: ChannelRowProps) {
  return (
    <a
      href={href}
      {...(external ? { target: "_blank", rel: "noopener noreferrer" } : {})}
      aria-label={`${label}: ${value}`}
      className={`neu-button flex items-center gap-3 rounded-2xl px-3.5 py-2.5 transition-all ${FOCUS_RING}`}
    >
      <span
        className={`neu-pressed-sm flex size-8 shrink-0 items-center justify-center rounded-full ${SURFACE}`}
      >
        <Icon aria-hidden="true" className="size-3.5 text-indigo-600 dark:text-indigo-400" />
      </span>
      <span className="min-w-0">
        <span className="block font-mono text-[0.6rem] font-bold uppercase tracking-[0.16em] text-slate-500 dark:text-slate-400">
          {label}
        </span>
        <span className="block truncate text-xs font-bold text-slate-800 dark:text-slate-200">
          {value}
        </span>
      </span>
    </a>
  );
}
