"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";
import { LogoHnamOfficial } from "../ui/logo-hnam-official";

export function SiteFooter() {
  const t = useTranslations("footer");

  return (
    <footer className="w-full bg-[#e0e5ec] dark:bg-[#1e222b] border-t border-slate-300/60 dark:border-slate-800/80 py-8 text-slate-600 dark:text-slate-300 transition-colors">
      <div className="mx-auto w-full max-w-7xl px-4 sm:px-6 md:px-8">
        <div className="flex flex-col items-center gap-5">
          <span className="font-mono text-xs sm:text-sm font-bold uppercase tracking-[0.25em] text-slate-800 dark:text-slate-200 select-none">
            Producer Workbench
          </span>

          <div className="relative flex flex-col md:flex-row justify-center items-center w-full gap-6 md:gap-12 lg:gap-16 py-1">
            <div className="hidden md:block absolute inset-x-0 top-1/2 -translate-y-1/2 h-[1px] bg-slate-300/70 dark:bg-slate-700/70 z-0" />

            <div className="relative flex items-center gap-3 shrink-0 bg-[#e0e5ec] dark:bg-[#1e222b] px-3 z-10">
              <div className="hidden sm:flex items-center gap-1 h-5 text-indigo-600 dark:text-indigo-400">
                <div className="w-[2px] h-2 bg-current rounded-full" />
                <div className="w-[2px] h-4 bg-current rounded-full" />
                <div className="w-[2px] h-1.5 bg-current rounded-full" />
                <div className="w-[2px] h-3 bg-current rounded-full" />
              </div>
              <Link
                href="/"
                className="px-2.5 py-0.5 border-2 border-slate-900 dark:border-slate-100 font-bold text-sm tracking-tight text-slate-900 dark:text-slate-100 shrink-0 focus-visible:outline-2 focus-visible:outline-indigo-600 focus-visible:outline-offset-2"
                aria-label="PWB Home"
              >
                PWB
              </Link>
            </div>

            <div className="relative z-10 bg-[#e0e5ec] dark:bg-[#1e222b] px-4 max-w-xl">
              <p className="text-xs leading-relaxed text-slate-600 dark:text-slate-400 text-center font-medium">
                {t("description")}
              </p>
            </div>

            <div className="relative flex items-center gap-3 shrink-0 bg-[#e0e5ec] dark:bg-[#1e222b] px-3 z-10">
              <LogoHnamOfficial className="h-7 w-auto text-slate-900 dark:text-slate-100" />
              <div className="hidden sm:flex items-center gap-1 h-5 text-indigo-600 dark:text-indigo-400">
                <div className="w-[2px] h-3 bg-current rounded-full" />
                <div className="w-[2px] h-1.5 bg-current rounded-full" />
                <div className="w-[2px] h-4 bg-current rounded-full" />
                <div className="w-[2px] h-2 bg-current rounded-full" />
              </div>
            </div>
          </div>

          <div className="flex flex-col items-center gap-1">
            <span className="text-[10px] tracking-wider uppercase font-bold text-slate-400 dark:text-slate-500">
              {t("contact")}
            </span>
            <a
              href="mailto:namnguyenduchoang@gmail.com"
              className="text-xs text-slate-600 hover:text-indigo-600 dark:text-slate-300 dark:hover:text-indigo-400 transition-colors underline decoration-slate-400 dark:decoration-slate-600 underline-offset-4 font-semibold focus-visible:outline-2 focus-visible:outline-indigo-600 focus-visible:outline-offset-2"
            >
              namnguyenduchoang@gmail.com
            </a>
          </div>

          <div className="w-full mt-2 border-t border-slate-300/60 dark:border-slate-700/60 pt-4 text-center">
            <p className="text-[10px] tracking-wider uppercase text-slate-500 dark:text-slate-400 font-medium">
              © {new Date().getFullYear()} Producer Workbench. {t("rights")}
            </p>
          </div>
        </div>
      </div>
    </footer>
  );
}
