"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";
import { ArrowLeft } from "lucide-react";
import { RoomCodeForm } from "@/features/liveroom/components/join/room-code-form";

export default function JoinLiveroomPage() {
  const t = useTranslations("liveroom.join");
  const tActions = useTranslations("voice.actions");

  return (
    <div className="flex flex-col gap-6 font-sans">
      <div className="flex items-center justify-between gap-3">
        <h1 className="text-xl font-bold tracking-tight text-foreground sm:text-2xl">
          {t("title")}
        </h1>
        <Link
          href="/dashboard/liveroom"
          className="key-press flex shrink-0 items-center gap-2 rounded-lg border border-border px-4 py-2 text-sm font-medium text-muted-foreground beat-16th transition-colors ease-hammer hover:bg-muted hover:text-foreground"
        >
          <ArrowLeft className="size-4" aria-hidden="true" />
          {tActions("back")}
        </Link>
      </div>
      <div className="mx-auto w-full max-w-md rounded-xl border border-neutral-200 border-t-4 border-t-black bg-white p-6 dark:border-neutral-800 dark:border-t-white dark:bg-black shadow-xs">
        <RoomCodeForm />
      </div>
    </div>
  );
}