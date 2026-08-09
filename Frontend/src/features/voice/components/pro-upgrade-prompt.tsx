"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";
import { Crown } from "lucide-react";
import { NEU_TEXT, NEU_TEXT_MUTED, NeuPanel, neuButton } from "@/components/ui/neu";

export function ProUpgradePrompt() {
  const t = useTranslations("voice.errors");
  const tActions = useTranslations("voice.actions");

  return (
    <NeuPanel
      tone="pressed"
      className="flex min-h-[60vh] flex-1 flex-col items-center justify-center gap-5 p-12 text-center"
    >
      <span className="neu-raised grid size-16 place-items-center rounded-full border-none">
        <Crown className="size-7 text-amber-500 dark:text-amber-400" aria-hidden="true" />
      </span>
      <h2 className={`text-xl font-bold tracking-tight ${NEU_TEXT}`}>{t("proOnly")}</h2>
      <p className={`max-w-md text-sm font-medium leading-relaxed ${NEU_TEXT_MUTED}`}>
        {tActions("configure")}
      </p>
      <Link href="/dashboard/songs" className={neuButton()}>
        {tActions("back")}
      </Link>
    </NeuPanel>
  );
}
