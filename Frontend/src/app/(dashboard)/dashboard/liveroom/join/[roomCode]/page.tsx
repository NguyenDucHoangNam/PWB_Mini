"use client";

import { use } from "react";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { ArrowLeft } from "lucide-react";
import { NeuScreen, neuButton } from "@/components/ui/neu";
import { JoinFlow } from "@/features/liveroom/components/join/join-flow";
import { JoinStepShell } from "@/features/liveroom/components/join/join-step-shell";
import { normalizeRoomCode } from "@/features/liveroom/utils/format-room-code";

export default function JoinLiveroomByCodePage({
  params,
}: {
  params: Promise<{ roomCode: string }>;
}) {
  const { roomCode } = use(params);
  const tActions = useTranslations("voice.actions");

  return (
    <NeuScreen className="relative font-sans">
      <Link
        href="/dashboard/liveroom/join"
        className={neuButton({}, "absolute right-0 top-0 z-10")}
      >
        <ArrowLeft className="size-4" aria-hidden="true" />
        {tActions("back")}
      </Link>

      <div className="flex flex-1 flex-col items-center justify-center pt-16 sm:pt-0">
        <JoinStepShell>
          <JoinFlow roomCode={normalizeRoomCode(roomCode)} />
        </JoinStepShell>
      </div>
    </NeuScreen>
  );
}
