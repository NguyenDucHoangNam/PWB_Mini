"use client";

import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { UserMinus } from "lucide-react";
import { NEU_TEXT, NEU_TEXT_MUTED, NeuButton, NeuPanel } from "@/components/ui/neu";
import { CountdownText } from "../ui/countdown-text";

export function KickedScreen({ cooldownUntil }: { cooldownUntil: string | null }) {
  const t = useTranslations("liveroom.room.kicked");
  const router = useRouter();

  return (
    <div className="flex h-dvh flex-col items-center justify-center bg-[#e0e5ec] p-6 dark:bg-[#1e222b]">
      <NeuPanel className="flex w-full max-w-md flex-col items-center gap-5 p-8 text-center">
      <span className="neu-pressed grid size-16 place-items-center rounded-full border-none text-rose-700 dark:text-rose-400">
          <UserMinus className="size-7" aria-hidden />
        </span>
      <h1 className={`text-xl font-bold md:text-2xl ${NEU_TEXT}`}>
        {t("title")}
      </h1>
      <p className={`max-w-sm text-sm font-medium ${NEU_TEXT_MUTED}`}>{t("hint")}</p>
      {cooldownUntil ? (
        <p className={`text-sm font-medium ${NEU_TEXT_MUTED}`}>
          {t("cooldownRemaining", { time: "" })}
          <CountdownText deadline={cooldownUntil} className="ml-1 font-medium tabular-nums" />
        </p>
      ) : null}
      <NeuButton variant="primary" onClick={() => router.push("/dashboard/liveroom")}>
        {t("backToList")}
      </NeuButton>
      </NeuPanel>
    </div>
  );
}