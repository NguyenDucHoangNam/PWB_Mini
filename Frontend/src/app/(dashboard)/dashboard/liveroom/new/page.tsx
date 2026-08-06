"use client";

import { useTranslations } from "next-intl";
import { Spinner } from "@/components/ui/spinner";
import { CreateRoomForm } from "@/features/liveroom/components/room-list/create-room-form";
import { useLiveroomProRedirect } from "@/features/liveroom/hooks/use-liveroom-pro-redirect";

export default function NewLiveroomPage() {
  const { resolving } = useLiveroomProRedirect();
  const t = useTranslations("liveroom.create");

  if (resolving) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <Spinner size="sm" />
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-6 font-sans">
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold tracking-tight text-black md:text-3xl dark:text-white">
          {t("title")}
        </h1>
        <p className="text-sm text-neutral-500 dark:text-neutral-400">{t("subtitle")}</p>
      </div>
      <div className="mx-auto w-full max-w-lg rounded-xl border border-neutral-200 bg-white p-4 md:p-6 dark:border-neutral-800 dark:bg-black">
        <CreateRoomForm />
      </div>
    </div>
  );
}
