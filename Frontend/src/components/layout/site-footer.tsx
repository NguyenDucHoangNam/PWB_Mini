"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";
import { LogoHnamOfficial } from "../ui/logo-hnam-official";

export function SiteFooter() {
  const t = useTranslations("footer");

  return (
    <footer className="w-full border-t border-neutral-200 bg-neutral-50/50 py-7 text-neutral-500 dark:border-neutral-800 dark:bg-neutral-950/50 dark:text-neutral-400">
      <div className="mx-auto w-full max-w-7xl px-4 sm:px-6 md:px-8">
        <div className="flex flex-col items-center gap-4">
          {/* Brand Title */}
          <span className="font-mono text-xs sm:text-sm font-bold uppercase tracking-[0.25em] text-neutral-800 dark:text-neutral-200 select-none">
            Producer Workbench
          </span>

          {/* Top section: Co-located 3 columns with connecting lines */}
          <div className="relative flex flex-col md:flex-row justify-center items-center w-full gap-6 md:gap-12 lg:gap-16 py-1">
            {/* Absolute horizontal line in the background (desktop only) */}
            <div className="hidden md:block absolute inset-x-0 top-1/2 -translate-y-1/2 h-[1px] bg-neutral-200 dark:bg-neutral-800 opacity-80 z-0" />

            {/* Left Column: Soundwave + PWB Logo */}
            <div className="relative flex items-center gap-3 shrink-0 bg-neutral-50 dark:bg-neutral-950 px-3 z-10">
              <div className="hidden sm:flex items-center gap-1 h-5 text-neutral-400 dark:text-neutral-600">
                <div className="w-[2px] h-2 bg-current rounded-full" />
                <div className="w-[2px] h-4 bg-current rounded-full" />
                <div className="w-[2px] h-1.5 bg-current rounded-full" />
                <div className="w-[2px] h-3 bg-current rounded-full" />
              </div>
              <Link
                href="/"
                className="px-2.5 py-0.5 border-2 border-black dark:border-white font-bold text-sm tracking-tight text-black dark:text-white hover:opacity-80 transition-opacity shrink-0"
                aria-label="PWB Home"
              >
                PWB
              </Link>
            </div>

            {/* Center Column: Description */}
            <div className="relative z-10 bg-neutral-50 dark:bg-neutral-950 px-4 max-w-xl">
              <p className="text-xs leading-relaxed text-neutral-500 dark:text-neutral-400 text-center">
                {t("description")}
              </p>
            </div>

            {/* Right Column: HNAM OFFICIAL Logo + Soundwave */}
            <div className="relative flex items-center gap-3 shrink-0 bg-neutral-50 dark:bg-neutral-950 px-3 z-10">
              <LogoHnamOfficial className="h-7 w-auto text-black dark:text-white" />
              <div className="hidden sm:flex items-center gap-1 h-5 text-neutral-400 dark:text-neutral-600">
                <div className="w-[2px] h-3 bg-current rounded-full" />
                <div className="w-[2px] h-1.5 bg-current rounded-full" />
                <div className="w-[2px] h-4 bg-current rounded-full" />
                <div className="w-[2px] h-2 bg-current rounded-full" />
              </div>
            </div>
          </div>

          {/* Support & Contact */}
          <div className="flex flex-col items-center gap-1">
            <span className="text-[10px] tracking-wider uppercase font-bold text-neutral-400 dark:text-neutral-500">
              {t("contact")}
            </span>
            <a
              href="mailto:namnguyenduchoang@gmail.com"
              className="text-xs text-neutral-600 hover:text-black dark:text-neutral-300 dark:hover:text-white transition-colors underline decoration-neutral-400 dark:decoration-neutral-600 underline-offset-4"
            >
              namnguyenduchoang@gmail.com
            </a>
          </div>

          {/* Bottom section: Copyright */}
          <div className="w-full mt-4 border-t border-neutral-200/60 pt-4 dark:border-neutral-800/60 text-center">
            <p className="text-[10px] tracking-wider uppercase text-neutral-500 dark:text-neutral-400">
              © {new Date().getFullYear()} Producer Workbench. {t("rights")}
            </p>
          </div>
        </div>
      </div>
    </footer>
  );
}
