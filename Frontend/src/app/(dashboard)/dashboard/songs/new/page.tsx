"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";
import { ArrowLeft, UploadCloud } from "lucide-react";
import { SongUploadForm } from "@/features/voice/components/song-upload-form";
import { PageHeader } from "@/components/layout/page-header";
import { NeuPanel, NeuScreen, neuButton } from "@/components/ui/neu";

export default function NewSongPage() {
  const t = useTranslations("voice.songs.form");
  const tActions = useTranslations("voice.actions");

  return (
    <NeuScreen>
      <PageHeader
        title={t("newSongTitle")}
        subtitle={t("newSongSubtitle")}
        icon={UploadCloud}
        actions={
          <Link href="/dashboard/songs" className={neuButton()}>
            <ArrowLeft className="size-4" aria-hidden="true" />
            {tActions("back")}
          </Link>
        }
      />

      <NeuPanel className="flex flex-1 flex-col p-5 sm:p-6">
        <SongUploadForm />
      </NeuPanel>
    </NeuScreen>
  );
}
