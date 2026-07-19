"use client";

import { useTranslations } from "next-intl";
import { ProUpgradePrompt } from "@/features/voice/components/pro-upgrade-prompt";
import { DashboardTabs } from "@/features/voice/components/dashboard-tabs";
import { useProGuard } from "@/features/auth/hooks/use-pro-guard";

export default function DashboardPage() {
  const { isPro } = useProGuard();
  const tDashboard = useTranslations("dashboard");

  if (!isPro) {
    return <ProUpgradePrompt />;
  }

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
      <DashboardTabs />
    </div>
  );
}