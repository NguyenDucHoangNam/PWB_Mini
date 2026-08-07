"use client";

import { use } from "react";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { ArrowLeft } from "lucide-react";
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
    <div className="relative flex flex-1 flex-col font-sans">
      <Link
        href="/dashboard/liveroom/join"
        className="key-press absolute right-0 top-0 z-10 inline-flex items-center gap-2 rounded-lg border border-border bg-background px-3 py-2 text-sm font-medium text-muted-foreground beat-16th transition-colors ease-hammer hover:bg-muted hover:text-foreground"
      >
        <ArrowLeft className="size-4" aria-hidden="true" />
        {tActions("back")}
      </Link>

      <div className="flex flex-1 flex-col items-center justify-center pt-14 sm:pt-0">
        <JoinStepShell>
          <JoinFlow roomCode={normalizeRoomCode(roomCode)} />
        </JoinStepShell>
      </div>
    </div>
  );
}
