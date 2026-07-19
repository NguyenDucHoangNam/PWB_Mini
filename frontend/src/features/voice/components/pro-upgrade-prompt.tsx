"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";

export function ProUpgradePrompt() {
  const t = useTranslations("voice.errors");
  const tActions = useTranslations("voice.actions");

  return (
    <div className="flex flex-col items-center justify-center gap-4 rounded-xl border border-neutral-200 bg-white p-12 text-center dark:border-neutral-800 dark:bg-black">
      <h2 className="text-xl font-bold text-black dark:text-white">{t("proOnly")}</h2>
      <p className="max-w-md text-sm text-neutral-500 dark:text-neutral-400">
        {tActions("configure")}
      </p>
      <Link href="/dashboard/songs">
        <Button variant="outline">{tActions("back")}</Button>
      </Link>
    </div>
  );
}