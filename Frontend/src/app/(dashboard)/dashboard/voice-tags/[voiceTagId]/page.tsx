"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

export default function VoiceTagDetailPage() {
  const router = useRouter();

  useEffect(() => {
    router.replace("/dashboard/voice-tags");
  }, [router]);

  return null;
}