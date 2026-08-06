"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { Search } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { JoinStepIndicator } from "./join-step-indicator";
import { isCompleteRoomCode, normalizeRoomCode } from "../../utils/format-room-code";
import { ROOM_CODE_LENGTH } from "../../types";

export function RoomCodeForm() {
  const t = useTranslations("liveroom.join");
  const router = useRouter();
  const [code, setCode] = useState("");

  const complete = isCompleteRoomCode(code);

  return (
    <form
      className="flex w-full flex-col gap-6"
      onSubmit={(event) => {
        event.preventDefault();
        if (complete) router.push(`/dashboard/liveroom/join/${normalizeRoomCode(code)}`);
      }}
    >
      <JoinStepIndicator current={1} />

      <div className="flex flex-col gap-2">
        <Label htmlFor="liveroom-code" className="font-semibold text-xs uppercase tracking-wide text-neutral-700 dark:text-neutral-300">
          {t("codeLabel")}
        </Label>
        <Input
          id="liveroom-code"
          value={code}
          onChange={(event) => setCode(normalizeRoomCode(event.target.value))}
          placeholder={t("codePlaceholder")}
          autoComplete="off"
          autoCapitalize="characters"
          spellCheck={false}
          inputMode="text"
          maxLength={ROOM_CODE_LENGTH}
          className="h-14 text-center font-mono text-2xl font-bold tracking-[0.4em] border-neutral-300 bg-neutral-50 focus:border-black dark:border-neutral-700 dark:bg-neutral-900 dark:focus:border-white"
        />
      </div>

      <Button
        type="submit"
        className="h-10 min-h-[44px] sm:min-h-0 w-full bg-black font-semibold text-white hover:bg-neutral-800 dark:bg-white dark:text-black dark:hover:bg-neutral-200 active:translate-y-[1px] transition-all"
        disabled={!complete}
      >
        <Search className="size-4 mr-1.5" />
        {t("lookup")}
      </Button>
    </form>
  );
}