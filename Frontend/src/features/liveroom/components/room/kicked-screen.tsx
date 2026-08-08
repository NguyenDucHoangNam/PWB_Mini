"use client";

import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { UserMinus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { CountdownText } from "../ui/countdown-text";

export function KickedScreen({ cooldownUntil }: { cooldownUntil: string | null }) {
  const t = useTranslations("liveroom.room.kicked");
  const router = useRouter();

  return (
    <div className="flex h-dvh flex-col items-center justify-center gap-4 bg-white p-6 text-center dark:bg-black">
      <UserMinus className="size-10 text-red-600 dark:text-red-400" aria-hidden />
      <h1 className="text-xl font-semibold text-black md:text-2xl dark:text-white">
        {t("title")}
      </h1>
      <p className="max-w-sm text-sm text-neutral-500 dark:text-neutral-400">{t("hint")}</p>
      {cooldownUntil ? (
        <p className="text-sm text-neutral-500 dark:text-neutral-400">
          {t("cooldownRemaining", { time: "" })}
          <CountdownText deadline={cooldownUntil} className="ml-1 font-medium tabular-nums" />
        </p>
      ) : null}
      <Button className="h-11 md:h-9" onClick={() => router.push("/dashboard/liveroom")}>
        {t("backToList")}
      </Button>
    </div>
  );
}