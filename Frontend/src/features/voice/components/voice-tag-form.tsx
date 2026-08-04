"use client";

import { useRouter } from "next/navigation";
import { TtsForm } from "./tts-form";

export function VoiceTagForm() {
  const router = useRouter();

  return <TtsForm onCancel={() => router.push("/dashboard/voice-tags")} />;
}

