"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";
import { ArrowLeft, UploadCloud, Music2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { SongUploadForm } from "@/features/voice/components/song-upload-form";
import { ProUpgradePrompt } from "@/features/voice/components/pro-upgrade-prompt";
import { useProGuard } from "@/features/auth/hooks/use-pro-guard";

export default function NewSongPage() {
  const { isPro } = useProGuard();
  const t = useTranslations("voice.songs.form");
  const tActions = useTranslations("voice.actions");

  if (!isPro) {
    return <ProUpgradePrompt />;
  }

  return (
    <div className="flex flex-col gap-6 font-sans">
      {/* Back button & Navigation Header */}
      <div className="flex items-center gap-3">
        <Link
          href="/dashboard/songs"
          aria-label={tActions("back")}
          className="flex size-9 items-center justify-center rounded-lg border border-neutral-200 bg-white hover:bg-neutral-100 dark:border-neutral-800 dark:bg-neutral-900 dark:hover:bg-neutral-800 transition-colors"
        >
          <ArrowLeft className="size-4 text-neutral-600 dark:text-neutral-400" />
        </Link>
        <div>
          <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-neutral-900 dark:text-neutral-100">
            {t("newSongTitle")}
          </h1>
          <p className="text-xs sm:text-sm text-neutral-500 dark:text-neutral-400 mt-0.5">
            {t("newSongSubtitle")}
          </p>
        </div>
      </div>

      {/* Upload Card */}
      <div className="rounded-xl border border-neutral-200 bg-white p-6 dark:border-neutral-800 dark:bg-neutral-950">
        <SongUploadForm />
      </div>
    </div>
  );
}