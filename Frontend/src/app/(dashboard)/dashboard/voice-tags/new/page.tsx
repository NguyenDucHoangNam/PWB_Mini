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
    <div className="flex w-full flex-col gap-5 sm:gap-6">
      <div className="flex items-start gap-3">
        <Link
          href="/dashboard/voice-tags"
          aria-label={tActions("back")}
          className="key-press flex size-11 shrink-0 items-center justify-center rounded-lg border border-border text-muted-foreground beat-16th transition-colors ease-hammer hover:bg-muted hover:text-foreground sm:size-9"
        >
          <ArrowLeft className="size-4" aria-hidden="true" />
        </Link>
        <div className="flex min-w-0 flex-col gap-1">
          <h1 className="text-xl font-semibold tracking-tight text-foreground sm:text-2xl">
            {t("title")}
          </h1>
          <p className="text-sm leading-relaxed text-muted-foreground">{t("subtitle")}</p>
        </div>
      </div>

      <div className="rounded-xl border border-border bg-card p-4 sm:p-6">
        <VoiceTagForm />
      </div>
    </div>
  );
}