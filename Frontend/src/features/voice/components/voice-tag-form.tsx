"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { ArrowLeft, ChevronRight, Mic, UploadCloud } from "lucide-react";
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
      className="key-press group flex cursor-pointer flex-col items-center gap-5 rounded-xl border border-border bg-card p-6 text-center beat-16th transition-all ease-hammer hover:border-foreground/25 hover:bg-muted/40 sm:p-8"
    >
      <span className="flex size-14 shrink-0 items-center justify-center rounded-2xl bg-primary text-primary-foreground sm:size-16">
        {icon}
      </span>
      <span className="flex flex-col gap-1.5">
        <span className="text-base font-bold tracking-tight text-foreground sm:text-lg">
          {title}
        </span>
        <span className="text-xs leading-relaxed text-muted-foreground sm:text-sm">
          {description}
        </span>
      </span>
      <span className="flex items-center gap-1 text-xs font-medium text-muted-foreground/60 beat-16th transition-colors ease-hammer group-hover:text-foreground">
        <ChevronRight
          className="size-3.5 beat-16th transition-transform ease-hammer group-hover:translate-x-0.5"
          aria-hidden="true"
        />
      </span>
    </button>
  );
}

export function VoiceTagForm() {
  const t = useTranslations("voice.voiceTags.create");
  const router = useRouter();
  const [mode, setMode] = useState<CreationMode | null>(null);

  const backToList = () => router.push("/dashboard/voice-tags");

  if (mode === "tts") {
    return <TtsForm onCancel={() => setMode(null)} />;
  }

  if (mode === "upload") {
    return <UploadVoiceTagForm onCancel={() => setMode(null)} />;
  }

  return (
    <div className="flex flex-1 flex-col gap-6">
      <p className="text-sm text-muted-foreground">{t("prompt")}</p>
      <div className="grid flex-1 content-center gap-4 sm:grid-cols-2 sm:gap-6">
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
