"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { ChevronRight, Mic, UploadCloud } from "lucide-react";
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
      className="key-press group flex cursor-pointer items-start gap-3.5 rounded-xl border border-border bg-card p-4 text-left beat-16th transition-colors ease-hammer hover:border-foreground/25 hover:bg-muted/40 sm:p-5"
    >
      <span className="mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-lg bg-primary text-primary-foreground">
        {icon}
      </span>
      <span className="flex min-w-0 flex-col gap-1">
        <span className="text-sm font-semibold text-foreground">{title}</span>
        <span className="text-xs leading-relaxed text-muted-foreground">{description}</span>
      </span>
      <ChevronRight
        className="ml-auto size-4 shrink-0 self-center text-muted-foreground beat-16th transition-transform ease-hammer group-hover:translate-x-0.5"
        aria-hidden="true"
      />
    </button>
  );
}

export function VoiceTagForm() {
  const t = useTranslations("voice.voiceTags.create");
  const router = useRouter();
  const [mode, setMode] = useState<CreationMode | null>(null);

  const backToList = () => router.push("/dashboard/voice-tags");

  if (mode === "tts") {
    // Cancelling steps back to the choice rather than out of the flow: the user picked a way to create a
    // tag, and changing their mind about which way should not throw the whole attempt away.
    return <TtsForm onCancel={() => setMode(null)} />;
  }

  if (mode === "upload") {
    return <UploadVoiceTagForm onCancel={() => setMode(null)} />;
  }

  return (
    <div className="flex flex-col gap-4">
      <p className="text-sm text-muted-foreground">{t("prompt")}</p>
      <div className="grid gap-3 sm:grid-cols-2 sm:gap-4">
        <ModeCard
          icon={<Mic className="size-4.5" aria-hidden="true" />}
          title={t("ttsTitle")}
          description={t("ttsDescription")}
          onSelect={() => setMode("tts")}
        />
        <ModeCard
          icon={<UploadCloud className="size-4.5" aria-hidden="true" />}
          title={t("uploadTitle")}
          description={t("uploadDescription")}
          onSelect={() => setMode("upload")}
        />
      </div>
      <div className="flex justify-end">
        <button
          type="button"
          onClick={backToList}
          className="text-sm font-medium text-muted-foreground underline-offset-4 hover:text-foreground hover:underline"
        >
          {t("backToList")}
        </button>
      </div>
    </div>
  );
}
