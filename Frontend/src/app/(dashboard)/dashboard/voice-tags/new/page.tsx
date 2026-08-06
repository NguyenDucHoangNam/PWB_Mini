"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";
import { ArrowLeft } from "lucide-react";
import { ProUpgradePrompt } from "@/features/voice/components/pro-upgrade-prompt";
import { VoiceTagForm } from "@/features/voice/components/voice-tag-form";
import { useProGuard } from "@/features/auth/hooks/use-pro-guard";

export default function NewVoiceTagPage() {
  const { isPro } = useProGuard();
  const t = useTranslations("voice.voiceTags");
  const tActions = useTranslations("voice.actions");

  if (!isPro) {
    return <ProUpgradePrompt />;
  }

  return (
    <div className="flex flex-col gap-6 font-sans">
      <div className="flex items-center gap-3">
        <Link
          href="/dashboard/voice-tags"
          aria-label={tActions("back")}
          className="flex size-9 min-h-[44px] sm:min-h-0 items-center justify-center rounded-lg border border-neutral-300 bg-neutral-100 hover:bg-neutral-200 dark:border-neutral-800 dark:bg-neutral-900 dark:hover:bg-neutral-800 transition-all active:translate-y-[1px]"
        >
          <ArrowLeft className="size-4 text-neutral-800 dark:text-neutral-200" />
        </Link>
        <div className="flex flex-col">
          <div className="flex items-center gap-2">
            <span className="h-5 w-1 rounded-full bg-black dark:bg-white" aria-hidden="true" />
            <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-neutral-900 dark:text-neutral-100">
              {t("title")}
            </h1>
          </div>
          <p className="text-xs sm:text-sm text-neutral-500 dark:text-neutral-400 mt-0.5 ml-3">
            {t("subtitle")}
          </p>
        </div>
      </div>

      <div className="rounded-xl border border-neutral-200 border-t-4 border-t-black bg-white p-6 dark:border-neutral-800 dark:border-t-white dark:bg-black shadow-xs">
        <VoiceTagForm />
      </div>
    </div>
  );
}