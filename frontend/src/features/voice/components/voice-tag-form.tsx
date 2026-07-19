"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { TtsForm } from "./tts-form";
import { UploadVoiceTagForm } from "./upload-voice-tag-form";

type Method = "SELECT" | "TTS" | "UPLOAD";

export function VoiceTagForm() {
  const t = useTranslations("voice.voiceTags.wizard");
  const tActions = useTranslations("voice.actions");
  const router = useRouter();
  const [method, setMethod] = useState<Method>("SELECT");

  if (method === "TTS") {
    return <TtsForm onCancel={() => setMethod("SELECT")} />;
  }

  if (method === "UPLOAD") {
    return <UploadVoiceTagForm onCancel={() => setMethod("SELECT")} />;
  }

  return (
    <div className="flex flex-col gap-4">
      <h2 className="text-base font-semibold text-black dark:text-white">{t("selectMethod")}</h2>
      <div className="grid gap-3 sm:grid-cols-2">
        <button
          type="button"
          onClick={() => setMethod("TTS")}
          aria-label={t("methodTts")}
          className="flex flex-col items-start gap-2 rounded-xl border border-neutral-200 bg-white p-4 text-left transition-colors hover:border-neutral-400 dark:border-neutral-800 dark:bg-black dark:hover:border-neutral-600"
        >
          <span className="text-sm font-semibold text-black dark:text-white">{t("methodTts")}</span>
          <span className="text-xs text-neutral-500 dark:text-neutral-400">{t("methodTtsDesc")}</span>
        </button>
        <button
          type="button"
          onClick={() => setMethod("UPLOAD")}
          aria-label={t("methodUpload")}
          className="flex flex-col items-start gap-2 rounded-xl border border-neutral-200 bg-white p-4 text-left transition-colors hover:border-neutral-400 dark:border-neutral-800 dark:bg-black dark:hover:border-neutral-600"
        >
          <span className="text-sm font-semibold text-black dark:text-white">{t("methodUpload")}</span>
          <span className="text-xs text-neutral-500 dark:text-neutral-400">{t("methodUploadDesc")}</span>
        </button>
      </div>
      <div className="flex justify-end">
        <Button type="button" variant="ghost" onClick={() => router.push("/dashboard/voice-tags")}>
          {tActions("cancel")}
        </Button>
      </div>
    </div>
  );
}
