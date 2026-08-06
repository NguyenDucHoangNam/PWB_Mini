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
      <div className="flex items-center gap-3">
        <Link
          href="/dashboard/liveroom"
          aria-label={tActions("back")}
          className="flex size-9 min-h-[44px] sm:min-h-0 items-center justify-center rounded-lg border border-neutral-300 bg-neutral-100 hover:bg-neutral-200 dark:border-neutral-800 dark:bg-neutral-900 dark:hover:bg-neutral-800 transition-all active:translate-y-[1px]"
        >
          <ArrowLeft className="size-4 text-neutral-800 dark:text-neutral-200" />
        </Link>
        <div className="flex items-center gap-2">
          <span className="h-5 w-1 rounded-full bg-black dark:bg-white" aria-hidden="true" />
          <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-neutral-900 dark:text-neutral-100">
            {t("title")}
          </h1>
        </div>
      </div>
      <div className="mx-auto w-full max-w-md rounded-xl border border-neutral-200 border-t-4 border-t-black bg-white p-6 dark:border-neutral-800 dark:border-t-white dark:bg-black shadow-xs">
        <RoomCodeForm />
      </div>
    </div>
  );
}