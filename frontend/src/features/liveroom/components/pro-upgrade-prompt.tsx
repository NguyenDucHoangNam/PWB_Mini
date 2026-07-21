"use client";

import { useTranslations } from "next-intl";
import Link from "next/link";
import { Button } from "@/components/ui/button";

export function ProUpgradePrompt() {
  const t = useTranslations("liveroom.errors");
  const tActions = useTranslations("liveroom.actions");

  return (
    <div className="flex flex-col items-center justify-center gap-4 rounded-xl border border-neutral-200 bg-white p-12 text-center dark:border-neutral-800 dark:bg-black">
      <h2 className="text-xl font-bold text-black dark:text-white">
        {t("hostAlreadyActive")}
      </h2>
      <p className="max-w-md text-sm text-neutral-500 dark:text-neutral-400">
        {t("proOnlyHost")}
      </p>
      <Link href="/dashboard/live-rooms">
        <Button variant="outline">{tActions("back")}</Button>
      </Link>
    </div>
  );
}
