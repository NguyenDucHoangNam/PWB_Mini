"use client";

import { useTranslations } from "next-intl";
import { ProUpgradePrompt } from "@/features/voice/components/pro-upgrade-prompt";
import { VoiceTagForm } from "@/features/voice/components/voice-tag-form";
import { useProGuard } from "@/features/auth/hooks/use-pro-guard";

export default function NewVoiceTagPage() {
  const { isPro } = useProGuard();
  const t = useTranslations("voice.voiceTags");

  if (!isPro) {
    return <ProUpgradePrompt />;
  }

  return (
    <div className="flex flex-col gap-6 font-sans">
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold tracking-tight text-black dark:text-white">
          {t("title")}
        </h1>
        <p className="text-sm text-neutral-500 dark:text-neutral-400">{t("subtitle")}</p>
      </div>
      <div className="rounded-xl border border-neutral-200 bg-white p-6 dark:border-neutral-800 dark:bg-black">
        <VoiceTagForm />
      </div>
    </div>
  );
}