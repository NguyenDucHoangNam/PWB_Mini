"use client";

import { useTranslations } from "next-intl";
import { RoomCodeForm } from "@/features/liveroom/components/join/room-code-form";

export default function JoinLiveroomPage() {
  const t = useTranslations("liveroom.join");

  return (
    <div className="flex flex-col gap-6 font-sans">
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold tracking-tight text-black md:text-3xl dark:text-white">
          {t("title")}
        </h1>
      </div>
      <div className="mx-auto w-full max-w-md rounded-xl border border-neutral-200 bg-white p-4 md:p-6 dark:border-neutral-800 dark:bg-black">
        <RoomCodeForm />
      </div>
    </div>
  );
}