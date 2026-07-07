"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";

export function SiteFooter() {
  const t = useTranslations("footer");

  return (
    <footer className="w-full border-t border-neutral-200 bg-neutral-50 py-12 text-neutral-500 dark:border-neutral-800 dark:bg-neutral-950 dark:text-neutral-400">
      <div className="mx-auto w-full max-w-7xl px-6 md:px-8">
        <div className="grid grid-cols-1 gap-8 md:grid-cols-4">
          <div className="flex flex-col gap-4">
            <Link href="/" className="text-lg font-bold tracking-tight text-black dark:text-white">
              PWB MiNi
            </Link>
            <p className="text-sm leading-relaxed max-w-xs">
              {t("description")}
            </p>
          </div>

          <div className="flex flex-col gap-3">
            <h3 className="text-sm font-semibold text-black dark:text-white">{t("product")}</h3>
            <Link href="/dashboard" className="text-sm hover:text-black dark:hover:text-white">
              {t("dashboard")}
            </Link>
            <Link href="/rooms" className="text-sm hover:text-black dark:hover:text-white">
              {t("liveRooms")}
            </Link>
            <Link href="/" className="text-sm hover:text-black dark:hover:text-white">
              {t("demoSharing")}
            </Link>
          </div>

          <div className="flex flex-col gap-3">
            <h3 className="text-sm font-semibold text-black dark:text-white">{t("support")}</h3>
            <span className="text-sm">{t("contact")}</span>
            <a
              href="mailto:support@pwbmini.com"
              className="text-sm text-neutral-800 hover:text-black dark:text-neutral-200 dark:hover:text-white font-medium"
            >
              support@pwbmini.com
            </a>
            <Link href="/" className="text-sm hover:text-black dark:hover:text-white">
              {t("faq")}
            </Link>
          </div>

          <div className="flex flex-col gap-3">
            <h3 className="text-sm font-semibold text-black dark:text-white">{t("legal")}</h3>
            <Link href="/" className="text-sm hover:text-black dark:hover:text-white">
              {t("terms")}
            </Link>
            <Link href="/" className="text-sm hover:text-black dark:hover:text-white">
              {t("privacy")}
            </Link>
          </div>
        </div>

        <div className="mt-12 border-t border-neutral-200 pt-6 text-center text-xs dark:border-neutral-800">
          <p>© {new Date().getFullYear()} PWB MiNi. {t("rights")}</p>
        </div>
      </div>
    </footer>
  );
}
