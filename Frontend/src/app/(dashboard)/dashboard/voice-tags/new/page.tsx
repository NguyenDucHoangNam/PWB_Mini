"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";
import { ArrowLeft, Mic } from "lucide-react";
import { ProUpgradePrompt } from "@/features/voice/components/pro-upgrade-prompt";
import { VoiceTagForm } from "@/features/voice/components/voice-tag-form";
import { useProGuard } from "@/features/auth/hooks/use-pro-guard";
import { PageHeader } from "@/components/layout/page-header";
import { NeuPanel, NeuScreen, neuButton } from "@/components/ui/neu";

export default function NewVoiceTagPage() {
  const { isPro } = useProGuard();
  const t = useTranslations("voice.voiceTags");
  const tActions = useTranslations("voice.actions");

  return (
    <NeuScreen>
      {isPro ? (
        <>
          <PageHeader
            title={t("title")}
            subtitle={t("subtitle")}
            icon={Mic}
            actions={
              <Link href="/dashboard/voice-tags" className={neuButton()}>
                <ArrowLeft className="size-4" aria-hidden="true" />
                {tActions("back")}
              </Link>
            }
          />

          <NeuPanel className="flex flex-1 flex-col p-5 sm:p-6">
            <VoiceTagForm />
          </NeuPanel>
        </>
      ) : (
        <ProUpgradePrompt />
      )}
    </NeuScreen>
  );
}
