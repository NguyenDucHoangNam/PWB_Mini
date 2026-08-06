"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";

export function LiveroomProPrompt() {
  const t = useTranslations("liveroom.create");
  const tList = useTranslations("liveroom.list");

  return (
    <div className="flex min-h-[60vh] flex-1 flex-col items-center justify-center gap-4 rounded-xl border border-neutral-200 bg-white p-8 text-center md:p-12 dark:border-neutral-800 dark:bg-black">
      <h2 className="text-xl font-bold text-black dark:text-white">{t("proOnly")}</h2>
      <p className="max-w-md text-sm text-neutral-500 dark:text-neutral-400">
        {t("proOnlyHint")}
      </p>
      <Link href="/dashboard/liveroom/join">
        <Button variant="outline" className="h-11 md:h-9">
          {tList("joinByCode")}
        </Button>
      </Link>
    </div>
  );
}