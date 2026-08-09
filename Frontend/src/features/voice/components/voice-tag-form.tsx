"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { ChevronRight, Mic, UploadCloud } from "lucide-react";
import { NEU_FOCUS, NEU_TEXT, NEU_TEXT_MUTED } from "@/components/ui/neu";
import { TtsForm } from "./tts-form";
import { UploadVoiceTagForm } from "./upload-voice-tag-form";

type CreationMode = "tts" | "upload";

interface ModeCardProps {
  icon: React.ReactNode;
  title: string;
  description: string;
  onSelect: () => void;
}

function ModeCard({ icon, title, description, onSelect }: ModeCardProps) {
  return (
    <button
      type="button"
      onClick={onSelect}
      className={`neu-tile group flex cursor-pointer flex-col items-center gap-5 rounded-3xl border-none p-6 text-center sm:p-8 ${NEU_FOCUS}`}
    >
      <span className="flex size-14 shrink-0 items-center justify-center rounded-2xl bg-indigo-600 text-white sm:size-16 dark:bg-indigo-500">
        {icon}
      </span>
      <span className="flex flex-col gap-1.5">
        <span className={`text-base font-bold tracking-tight sm:text-lg ${NEU_TEXT}`}>{title}</span>
        <span className={`text-xs leading-relaxed sm:text-sm ${NEU_TEXT_MUTED}`}>{description}</span>
      </span>
      <span className="neu-raised-sm flex size-9 items-center justify-center rounded-full border-none text-indigo-600 dark:text-indigo-400">
        <ChevronRight
          className="size-4 beat-16th transition-transform ease-hammer group-hover:translate-x-0.5 motion-reduce:transition-none"
          aria-hidden="true"
        />
      </span>
    </button>
  );
}

export function VoiceTagForm() {
  const t = useTranslations("voice.voiceTags.create");
  const [mode, setMode] = useState<CreationMode | null>(null);

  if (mode === "tts") {
    return <TtsForm onCancel={() => setMode(null)} />;
  }

  if (mode === "upload") {
    return <UploadVoiceTagForm onCancel={() => setMode(null)} />;
  }

  return (
    <div className="flex flex-1 flex-col gap-6">
      <p className={`text-sm font-medium ${NEU_TEXT_MUTED}`}>{t("prompt")}</p>
      <div className="grid flex-1 content-center gap-5 sm:grid-cols-2 sm:gap-6">
        <ModeCard
          icon={<Mic className="size-6 sm:size-7" aria-hidden="true" />}
          title={t("ttsTitle")}
          description={t("ttsDescription")}
          onSelect={() => setMode("tts")}
        />
        <ModeCard
          icon={<UploadCloud className="size-6 sm:size-7" aria-hidden="true" />}
          title={t("uploadTitle")}
          description={t("uploadDescription")}
          onSelect={() => setMode("upload")}
        />
      </div>
    </div>
  );
}
