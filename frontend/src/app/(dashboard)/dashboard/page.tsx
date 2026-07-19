"use client";

import { useTranslations } from "next-intl";

export default function DashboardPage() {
  const tDashboard = useTranslations("dashboard");

  return (
    <div className="flex flex-col gap-6 font-sans">
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold tracking-tight text-black dark:text-white">
          {tDashboard("title")}
        </h1>
        <p className="text-sm text-neutral-500 dark:text-neutral-400">
          {tDashboard("subtitle")}
        </p>
      </div>
    </div>
  );
}
