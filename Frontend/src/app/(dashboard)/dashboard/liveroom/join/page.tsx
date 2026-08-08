"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";
import { ArrowLeft } from "lucide-react";
import { JoinStepShell } from "@/features/liveroom/components/join/join-step-shell";
import { RoomCodeForm } from "@/features/liveroom/components/join/room-code-form";

export default function JoinLiveroomPage() {
  const tActions = useTranslations("voice.actions");

  return (
    <div className="relative flex flex-1 flex-col font-sans">
      <Link
        href="/dashboard/liveroom"
        className="key-press absolute right-0 top-0 z-10 inline-flex items-center gap-2 rounded-lg border border-border bg-background px-3 py-2 text-sm font-medium text-muted-foreground beat-16th transition-colors ease-hammer hover:bg-muted hover:text-foreground"
      >
        <ArrowLeft className="size-4" aria-hidden="true" />
        {tActions("back")}
      </Link>

      <div className="flex flex-1 flex-col items-center justify-center pt-14 sm:pt-0">
        <JoinStepShell>
          <div className="flex w-full flex-1 flex-col justify-center rounded-xl border border-border bg-card p-6 shadow-xs sm:p-8">
            <RoomCodeForm />
          </div>
        </JoinStepShell>
      </div>
    </div>
  );
}
