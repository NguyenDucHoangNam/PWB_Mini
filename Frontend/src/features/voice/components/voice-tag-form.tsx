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
      className="group flex items-start gap-3.5 rounded-xl border border-neutral-200 bg-white p-4 text-left transition-all hover:border-neutral-400 hover:shadow-sm dark:border-neutral-800 dark:bg-black dark:hover:border-neutral-600"
    >
      <span className="mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-lg bg-neutral-100 text-neutral-700 dark:bg-neutral-900 dark:text-neutral-300">
        {icon}
      </span>
      <span className="flex min-w-0 flex-col gap-1">
        <span className="text-sm font-semibold text-neutral-900 dark:text-neutral-100">{title}</span>
        <span className="text-xs leading-relaxed text-neutral-500 dark:text-neutral-400">
          {description}
        </span>
      </span>
      <ChevronRight
        className="ml-auto size-4 shrink-0 self-center text-neutral-400 transition-transform group-hover:translate-x-0.5"
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
      <p className="text-sm text-neutral-600 dark:text-neutral-400">{t("prompt")}</p>
      <div className="grid gap-3 sm:grid-cols-2">
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
          className="text-xs font-medium text-neutral-500 underline-offset-4 hover:underline dark:text-neutral-400"
        >
          {t("backToList")}
        </button>
      </div>
    </div>
  );
}
