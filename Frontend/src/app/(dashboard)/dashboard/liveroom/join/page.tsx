"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";
import { ArrowLeft } from "lucide-react";
import { NeuPanel, NeuScreen, neuButton } from "@/components/ui/neu";
import { JoinStepShell } from "@/features/liveroom/components/join/join-step-shell";
import { RoomCodeForm } from "@/features/liveroom/components/join/room-code-form";

export default function JoinLiveroomPage() {
  const tActions = useTranslations("voice.actions");

  return (
    <NeuScreen className="relative font-sans">
      <Link
        href="/dashboard/liveroom"
        className={neuButton({}, "absolute right-0 top-0 z-10")}
      >
        <ArrowLeft className="size-4" aria-hidden="true" />
        {tActions("back")}
      </Link>

      <div className="flex flex-1 flex-col items-center justify-center pt-16 sm:pt-0">
        <JoinStepShell>
          <NeuPanel className="flex w-full flex-1 flex-col justify-center p-6 sm:p-8">
            <RoomCodeForm />
          </NeuPanel>
        </JoinStepShell>
      </div>
    </NeuScreen>
  );
}
