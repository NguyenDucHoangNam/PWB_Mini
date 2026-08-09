"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { KeyRound, Search } from "lucide-react";
import {
  NEU_INPUT,
  NEU_LABEL,
  NEU_TEXT,
  NEU_TEXT_MUTED,
  NeuButton,
} from "@/components/ui/neu";
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

      <div className="flex flex-col items-center gap-3.5 text-center">
        <div className="neu-raised grid size-14 place-items-center rounded-2xl border-none text-indigo-600 dark:text-indigo-400">
          <KeyRound className="size-5" aria-hidden="true" />
        </div>
        <div className="flex flex-col gap-1.5">
          <h2 className={`text-lg font-bold tracking-tight ${NEU_TEXT}`}>{t("title")}</h2>
          <p className={`text-sm font-medium ${NEU_TEXT_MUTED}`}>{t("subtitle")}</p>
        </div>
      </div>

      <div className="flex flex-col gap-2">
        <label htmlFor="liveroom-code" className={NEU_LABEL}>
          {t("codeLabel")}
        </label>
        <input
          id="liveroom-code"
          value={code}
          onChange={(event) => setCode(normalizeRoomCode(event.target.value))}
          placeholder={t("codePlaceholder")}
          autoComplete="off"
          autoCapitalize="characters"
          spellCheck={false}
          inputMode="text"
          maxLength={ROOM_CODE_LENGTH}
          className={`${NEU_INPUT} h-16 rounded-3xl text-center font-mono text-2xl font-bold tracking-[0.4em]`}
        />
      </div>

      <NeuButton type="submit" variant="primary" size="lg" className="w-full" disabled={!complete}>
        <Search className="size-4" aria-hidden="true" />
        {t("lookup")}
      </NeuButton>
    </form>
  );
}
