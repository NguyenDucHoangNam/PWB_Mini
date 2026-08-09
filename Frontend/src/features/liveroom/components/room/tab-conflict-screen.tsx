"use client";

import { useTranslations } from "next-intl";
import { Copy } from "lucide-react";
import { NEU_TEXT, NEU_TEXT_MUTED, NeuButton, NeuPanel } from "@/components/ui/neu";

interface TabConflictScreenProps {
  onFocusOther: () => void;
  onTakeOver: () => void;
}

export function TabConflictScreen({ onFocusOther, onTakeOver }: TabConflictScreenProps) {
  const t = useTranslations("liveroom.room.tabConflict");

  return (
    <div className="flex h-dvh flex-col items-center justify-center bg-[#e0e5ec] p-6 dark:bg-[#1e222b]">
      <NeuPanel className="flex w-full max-w-md flex-col items-center gap-5 p-8 text-center">
      <span className="neu-pressed grid size-16 place-items-center rounded-full border-none text-indigo-600 dark:text-indigo-400">
          <Copy className="size-7" aria-hidden />
        </span>
      <h1 className={`text-xl font-bold md:text-2xl ${NEU_TEXT}`}>
        {t("title")}
      </h1>
      <p className={`max-w-sm text-sm font-medium ${NEU_TEXT_MUTED}`}>{t("hint")}</p>
      <div className="flex w-full flex-col gap-3 sm:w-auto sm:flex-row">
        <NeuButton variant="primary" onClick={onFocusOther}>
          {t("switchTab")}
        </NeuButton>
        <NeuButton onClick={onTakeOver}>
          {t("closeTab")}
        </NeuButton>
      </div>
      </NeuPanel>
    </div>
  );
}