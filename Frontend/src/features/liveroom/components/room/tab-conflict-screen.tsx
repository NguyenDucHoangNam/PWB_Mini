"use client";

import { useTranslations } from "next-intl";
import { Copy } from "lucide-react";
import { Button } from "@/components/ui/button";

interface TabConflictScreenProps {
  onFocusOther: () => void;
  onTakeOver: () => void;
}

export function TabConflictScreen({ onFocusOther, onTakeOver }: TabConflictScreenProps) {
  const t = useTranslations("liveroom.room.tabConflict");

  return (
    <div className="flex h-dvh flex-col items-center justify-center gap-4 bg-white p-6 text-center dark:bg-black">
      <Copy className="size-10 text-neutral-400" aria-hidden />
      <h1 className="text-xl font-semibold text-black md:text-2xl dark:text-white">
        {t("title")}
      </h1>
      <p className="max-w-sm text-sm text-neutral-500 dark:text-neutral-400">{t("hint")}</p>
      <div className="flex w-full flex-col gap-2 sm:w-auto sm:flex-row">
        <Button className="h-11 md:h-9" onClick={onFocusOther}>
          {t("switchTab")}
        </Button>
        <Button variant="outline" className="h-11 md:h-9" onClick={onTakeOver}>
          {t("closeTab")}
        </Button>
      </div>
    </div>
  );
}