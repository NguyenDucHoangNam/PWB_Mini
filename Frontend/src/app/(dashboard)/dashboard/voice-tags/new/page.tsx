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
    <div className="flex w-full flex-1 flex-col gap-5 sm:gap-6">
      <div className="flex items-center justify-between gap-3">
        <div className="flex min-w-0 flex-col gap-0.5">
          <h1 className="text-xl font-bold tracking-tight text-foreground sm:text-2xl">
            {t("title")}
          </h1>
          <p className="text-xs uppercase tracking-widest text-muted-foreground/70">{t("subtitle")}</p>
        </div>
        <Link
          href="/dashboard/voice-tags"
          className="key-press flex shrink-0 items-center gap-2 rounded-lg border border-border px-4 py-2 text-sm font-medium text-muted-foreground beat-16th transition-colors ease-hammer hover:bg-muted hover:text-foreground"
        >
          <ArrowLeft className="size-4" aria-hidden="true" />
          {tActions("back")}
        </Link>
      </div>

      <div className="flex flex-1 flex-col rounded-xl border border-border bg-card p-4 sm:p-6">
        <VoiceTagForm />
      </div>
    </div>
  );
}