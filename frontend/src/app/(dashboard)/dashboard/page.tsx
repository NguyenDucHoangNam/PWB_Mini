"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";

export default function DashboardPage() {
  const t = useTranslations("dashboard");

  return (
    <div className="flex flex-col gap-6 font-sans">
      <div className="flex flex-col gap-2">
        <h1 className="text-2xl font-bold tracking-tight text-black dark:text-white">
          {t("title")}
        </h1>
        <p className="text-sm text-neutral-500 dark:text-neutral-400">
          {t("subtitle")}
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mt-4">
        {/* Profile Settings Card */}
        <div className="border border-neutral-200 dark:border-neutral-800 bg-white dark:bg-black rounded-xl p-6 flex flex-col justify-between">
          <div>
            <h2 className="text-lg font-bold text-black dark:text-white mb-2">{t("profileCardTitle")}</h2>
            <p className="text-sm text-neutral-500 dark:text-neutral-400 mb-6">
              {t("profileCardDesc")}
            </p>
          </div>
          <Link href="/profile">
            <Button variant="outline" className="w-full justify-center">
              {t("profileCardBtn")}
            </Button>
          </Link>
        </div>

        {/* Sessions Card */}
        <div className="border border-neutral-200 dark:border-neutral-800 bg-white dark:bg-black rounded-xl p-6 flex flex-col justify-between">
          <div>
            <h2 className="text-lg font-bold text-black dark:text-white mb-2">{t("sessionsCardTitle")}</h2>
            <p className="text-sm text-neutral-500 dark:text-neutral-400 mb-6">
              {t("sessionsCardDesc")}
            </p>
          </div>
          <Link href="/sessions">
            <Button variant="outline" className="w-full justify-center">
              {t("sessionsCardBtn")}
            </Button>
          </Link>
        </div>
      </div>
    </div>
  );
}
